package com.foodie.wallet.service.impl;

import com.foodie.common.dto.PaginationMeta;
import com.foodie.common.enums.LedgerReferenceType;
import com.foodie.common.enums.OwnerType;
import com.foodie.common.enums.PayoutStatus;
import com.foodie.common.exception.BadRequestException;
import com.foodie.common.exception.ErrorCode;
import com.foodie.common.exception.ResourceNotFoundException;
import com.foodie.common.exception.UnprocessableEntityException;
import com.foodie.restaurant.entity.Restaurant;
import com.foodie.restaurant.repository.RestaurantRepository;
import com.foodie.shared.contract.DeliveryPartnerLookup;
import com.foodie.shared.event.PayoutRequestedEvent;
import com.foodie.shared.event.WalletCreditedEvent;
import com.foodie.shared.event.WalletDebitedEvent;
import com.foodie.wallet.WalletConstants;
import com.foodie.wallet.dto.request.PayoutRequestDto;
import com.foodie.wallet.dto.response.LedgerEntryResponseDto;
import com.foodie.wallet.dto.response.PayoutResponseDto;
import com.foodie.wallet.dto.response.WalletBalanceResponseDto;
import com.foodie.wallet.entity.LedgerEntry;
import com.foodie.wallet.entity.Payout;
import com.foodie.wallet.entity.WalletAccount;
import com.foodie.wallet.mapper.WalletMapper;
import com.foodie.wallet.repository.LedgerEntryRepository;
import com.foodie.wallet.repository.PayoutRepository;
import com.foodie.wallet.repository.WalletAccountRepository;
import com.foodie.wallet.service.PayoutIdempotencyStore;
import com.foodie.wallet.service.WalletService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import com.foodie.infrastructure.razorpay.RazorpayClient;
import com.foodie.infrastructure.razorpay.RazorpayProperties;
import com.foodie.infrastructure.razorpay.RazorpaySignatureVerifier;
import com.foodie.wallet.dto.request.VerifyDepositRequestDto;
import com.foodie.wallet.dto.response.DepositOrderResponseDto;
import com.foodie.wallet.dto.response.VerifyDepositResponseDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WalletServiceImpl implements WalletService {

    private static final Logger log = LoggerFactory.getLogger(WalletServiceImpl.class);
    private static final EnumSet<PayoutStatus> OPEN_PAYOUT_STATUSES = EnumSet.of(PayoutStatus.REQUESTED,
            PayoutStatus.PROCESSING);

    private final WalletAccountRepository walletAccountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final PayoutRepository payoutRepository;
    private final DeliveryPartnerLookup deliveryPartnerLookup;
    private final RestaurantRepository restaurantRepository;
    private final PayoutIdempotencyStore payoutIdempotencyStore;
    private final ApplicationEventPublisher eventPublisher;
    private final RazorpayClient razorpayClient;
    private final RazorpayProperties razorpayProperties;
    private final RazorpaySignatureVerifier razorpaySignatureVerifier;

    public WalletServiceImpl(
            WalletAccountRepository walletAccountRepository,
            LedgerEntryRepository ledgerEntryRepository,
            PayoutRepository payoutRepository,
            DeliveryPartnerLookup deliveryPartnerLookup,
            RestaurantRepository restaurantRepository,
            PayoutIdempotencyStore payoutIdempotencyStore,
            ApplicationEventPublisher eventPublisher) {
        this(
                walletAccountRepository,
                ledgerEntryRepository,
                payoutRepository,
                deliveryPartnerLookup,
                restaurantRepository,
                payoutIdempotencyStore,
                eventPublisher,
                null,
                new RazorpayProperties(),
                null
        );
    }

    @Autowired
    public WalletServiceImpl(
            WalletAccountRepository walletAccountRepository,
            LedgerEntryRepository ledgerEntryRepository,
            PayoutRepository payoutRepository,
            DeliveryPartnerLookup deliveryPartnerLookup,
            RestaurantRepository restaurantRepository,
            PayoutIdempotencyStore payoutIdempotencyStore,
            ApplicationEventPublisher eventPublisher,
            @Autowired(required = false) RazorpayClient razorpayClient,
            @Autowired(required = false) RazorpayProperties razorpayProperties,
            @Autowired(required = false) RazorpaySignatureVerifier razorpaySignatureVerifier) {
        this.walletAccountRepository = walletAccountRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.payoutRepository = payoutRepository;
        this.deliveryPartnerLookup = deliveryPartnerLookup;
        this.restaurantRepository = restaurantRepository;
        this.payoutIdempotencyStore = payoutIdempotencyStore;
        this.eventPublisher = eventPublisher;
        this.razorpayClient = razorpayClient;
        this.razorpayProperties = razorpayProperties != null ? razorpayProperties : new RazorpayProperties();
        this.razorpaySignatureVerifier = razorpaySignatureVerifier;
    }

    @Override
    @Transactional
    public DepositOrderResponseDto createDepositOrder(UUID userCredentialId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Deposit amount must be greater than zero.");
        }
        UUID partnerId = requirePartnerId(userCredentialId);
        getOrCreate(OwnerType.DELIVERY_PARTNER, partnerId);

        BigDecimal scaled = amount.setScale(2, RoundingMode.HALF_UP);
        String receipt = "dep_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String keyId = razorpayProperties.getKeyId();

        String orderId;
        if (razorpayClient != null) {
            RazorpayClient.RazorpayOrderCreateResult result = razorpayClient.createOrder(
                    scaled, receipt, "COD Deposit partner=" + partnerId);
            orderId = result.razorpayOrderId();
        } else {
            orderId = "order_stub_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
        }

        log.info("Created Razorpay COD deposit order: orderId={} amount={} partnerId={}",
                orderId, scaled, partnerId);

        return new DepositOrderResponseDto(
                orderId,
                scaled,
                "INR",
                keyId
        );
    }

    @Override
    @Transactional
    public VerifyDepositResponseDto verifyAndProcessDeposit(UUID userCredentialId, VerifyDepositRequestDto request) {
        UUID partnerId = requirePartnerId(userCredentialId);

        boolean isStub = razorpayProperties.isStub();
        if (!isStub && razorpaySignatureVerifier != null) {
            boolean valid = razorpaySignatureVerifier.isValidPaymentSignature(
                    request.razorpayOrderId(),
                    request.razorpayPaymentId(),
                    request.razorpaySignature()
            );
            if (!valid) {
                log.error("Razorpay signature verification failed for deposit: orderId={} paymentId={}",
                        request.razorpayOrderId(), request.razorpayPaymentId());
                throw new BadRequestException("Invalid Razorpay payment signature.");
            }
        } else {
            log.info("Stub mode or verifier bypass active for orderId={}", request.razorpayOrderId());
        }

        UUID referenceId = UUID.nameUUIDFromBytes(request.razorpayPaymentId().getBytes());
        credit(OwnerType.DELIVERY_PARTNER, partnerId, request.amount(), LedgerReferenceType.COD_DEPOSIT, referenceId);

        WalletBalanceResponseDto updatedBalance = getBalance(userCredentialId);
        log.info("Successfully verified and processed COD deposit: paymentId={} amount={} partnerId={}",
                request.razorpayPaymentId(), request.amount(), partnerId);

        return new VerifyDepositResponseDto(
                true,
                "Deposit of ₹" + request.amount() + " completed successfully.",
                request.razorpayPaymentId(),
                updatedBalance.availableBalance()
        );
    }

    @Override
    @Transactional
    public com.foodie.wallet.dto.response.CodBalanceResponseDto recordCodCashCollection(UUID userCredentialId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("COD cash amount must be greater than zero.");
        }
        UUID partnerId = requirePartnerId(userCredentialId);
        WalletAccount account = getOrCreate(OwnerType.DELIVERY_PARTNER, partnerId);

        BigDecimal scaled = amount.setScale(2, RoundingMode.HALF_UP);
        UUID referenceId = UUID.randomUUID();
        ledgerEntryRepository.save(LedgerEntry.credit(account.getId(), scaled, LedgerReferenceType.COD_COLLECTED, referenceId));

        log.info("Recorded COD cash collection: partnerId={} amount={}", partnerId, scaled);
        return getCodBalance(userCredentialId);
    }

    @Override
    @Transactional(readOnly = true)
    public com.foodie.wallet.dto.response.CodBalanceResponseDto getCodBalance(UUID userCredentialId) {
        UUID partnerId = requirePartnerId(userCredentialId);
        WalletAccount account = getOrCreate(OwnerType.DELIVERY_PARTNER, partnerId);

        BigDecimal totalCollected = ledgerEntryRepository.sumAmountByWalletAccountIdAndReferenceType(account.getId(), LedgerReferenceType.COD_COLLECTED);
        BigDecimal totalDeposited = ledgerEntryRepository.sumAmountByWalletAccountIdAndReferenceType(account.getId(), LedgerReferenceType.COD_DEPOSIT);

        BigDecimal pending = totalCollected.subtract(totalDeposited);
        if (pending.compareTo(BigDecimal.ZERO) < 0) {
            pending = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        return new com.foodie.wallet.dto.response.CodBalanceResponseDto(
                totalCollected.setScale(2, RoundingMode.HALF_UP),
                totalDeposited.setScale(2, RoundingMode.HALF_UP),
                pending.setScale(2, RoundingMode.HALF_UP)
        );
    }

    @Override
    @Transactional(readOnly = true)
    public WalletBalanceResponseDto getBalance(UUID userCredentialId) {
        UUID partnerId = requirePartnerId(userCredentialId);
        WalletAccount account = getOrCreate(OwnerType.DELIVERY_PARTNER, partnerId);
        BigDecimal openPayouts = payoutRepository.sumAmountByWalletAccountIdAndStatusIn(
                account.getId(), OPEN_PAYOUT_STATUSES);
        BigDecimal available = account.getBalance().subtract(openPayouts);
        if (available.compareTo(BigDecimal.ZERO) < 0) {
            available = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal totalEarnings = ledgerEntryRepository.sumCreditAmountByWalletAccountId(account.getId());
        BigDecimal totalPayouts = payoutRepository.sumCompletedAmountByWalletAccountId(account.getId());

        return WalletMapper.toBalance(
                account,
                available.setScale(2, RoundingMode.HALF_UP),
                openPayouts.setScale(2, RoundingMode.HALF_UP),
                totalEarnings.setScale(2, RoundingMode.HALF_UP),
                totalPayouts.setScale(2, RoundingMode.HALF_UP));
    }

    @Override
    @Transactional
    public PageResult<LedgerEntryResponseDto> getLedger(
            UUID userCredentialId,
            int page,
            int size,
            String sort,
            Instant createdAtFrom,
            Instant createdAtTo) {
        UUID partnerId = requirePartnerId(userCredentialId);
        WalletAccount account = getOrCreate(OwnerType.DELIVERY_PARTNER, partnerId);
        Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size), resolveSort(sort));
        Page<LedgerEntry> result;

        if (createdAtFrom == null && createdAtTo == null) {
            result = ledgerEntryRepository.findByWalletAccountId(account.getId(), pageable);
        } else {
            result = ledgerEntryRepository.findHistory(
                    account.getId(), createdAtFrom, createdAtTo, pageable);
        }

        List<LedgerEntryResponseDto> items = result.getContent().stream()
                .map(WalletMapper::toLedger)
                .toList();
        PaginationMeta meta = new PaginationMeta(
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
        return new PageResult<>(items, meta);
    }

    @Override
    @Transactional
    public PayoutResponseDto requestPayout(
            UUID userCredentialId,
            PayoutRequestDto request,
            String idempotencyKey) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var cached = payoutIdempotencyStore.find(idempotencyKey.trim());
            if (cached.isPresent()) {
                return cached.get();
            }
        }

        UUID partnerId = requirePartnerId(userCredentialId);
        WalletAccount account = getOrCreateForPessimisticUpdate(OwnerType.DELIVERY_PARTNER, partnerId);
        BigDecimal amount = request.amount().setScale(2, RoundingMode.HALF_UP);

        BigDecimal openPayouts = payoutRepository.sumAmountByWalletAccountIdAndStatusIn(
                account.getId(), OPEN_PAYOUT_STATUSES);
        BigDecimal available = account.getBalance().subtract(openPayouts);
        if (amount.compareTo(available) > 0) {
            throw new UnprocessableEntityException(
                    ErrorCode.INSUFFICIENT_BALANCE,
                    "Requested payout exceeds available wallet balance.");
        }

        // REQUESTED does not debit the ledger — bank settlement will.
        Payout payout = payoutRepository.save(Payout.request(account.getId(), amount, request.accountHolderName(),
                request.accountNumber(), request.ifscCode(), request.bankName()));
        PayoutResponseDto response = WalletMapper.toPayout(payout);
        eventPublisher.publishEvent(PayoutRequestedEvent.of(
                payout.getId(), account.getId(), partnerId, amount));

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            payoutIdempotencyStore.store(idempotencyKey.trim(), response);
        }
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<PayoutResponseDto> getPayoutHistory(UUID userCredentialId, int page, int size) {
        UUID partnerId = requirePartnerId(userCredentialId);
        WalletAccount account = getOrCreate(OwnerType.DELIVERY_PARTNER, partnerId);
        Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size), Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Payout> result = payoutRepository.findByWalletAccountId(account.getId(), pageable);
        List<PayoutResponseDto> items = result.getContent().stream()
                .map(WalletMapper::toPayout)
                .toList();
        PaginationMeta meta = new PaginationMeta(
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
        return new PageResult<>(items, meta);
    }

    @Override
    @Transactional(readOnly = true)
    public PayoutResponseDto getPayoutDetail(UUID userCredentialId, UUID payoutId) {
        UUID partnerId = requirePartnerId(userCredentialId);
        WalletAccount account = getOrCreate(OwnerType.DELIVERY_PARTNER, partnerId);
        Payout payout = payoutRepository.findByIdAndWalletAccountId(payoutId, account.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Payout request not found."));
        return WalletMapper.toPayout(payout);
    }

    @Override
    @Transactional
    public PayoutResponseDto updatePayoutStatus(
            UUID payoutId,
            PayoutStatus newStatus,
            String bankRef,
            String failureReason) {
        Payout payout = payoutRepository.findById(payoutId)
                .orElseThrow(() -> new ResourceNotFoundException("Payout not found with id: " + payoutId));

        WalletAccount account = walletAccountRepository.findById(payout.getWalletAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Associated wallet account not found."));

        // Pessimistic write lock on wallet account to prevent concurrent mutations
        walletAccountRepository.findByOwnerTypeAndOwnerIdForPessimisticUpdate(
                account.getOwnerType(), account.getOwnerId());

        // Re-fetch payout under pessimistic write lock to prevent race conditions
        payout = payoutRepository.findById(payoutId)
                .orElseThrow(() -> new ResourceNotFoundException("Payout not found with id: " + payoutId));

        if (payout.getStatus() == PayoutStatus.COMPLETED || payout.getStatus() == PayoutStatus.FAILED) {
            log.info("Payout {} already in terminal status {}", payoutId, payout.getStatus());
            return WalletMapper.toPayout(payout);
        }

        if (newStatus == PayoutStatus.COMPLETED) {
            payout.complete(bankRef);
            payout = payoutRepository.save(payout);

            debit(
                    account.getOwnerType(),
                    account.getOwnerId(),
                    payout.getAmount(),
                    LedgerReferenceType.PAYOUT,
                    payout.getId());

        } else if (newStatus == PayoutStatus.FAILED) {
            payout.fail(bankRef);
            payout = payoutRepository.save(payout);
        } else if (newStatus == PayoutStatus.PROCESSING) {
            payout.markProcessing();
            payout = payoutRepository.save(payout);
        }

        return WalletMapper.toPayout(payout);
    }

    @Override
    @Transactional
    public LedgerEntryResponseDto credit(
            OwnerType ownerType,
            UUID ownerId,
            BigDecimal amount,
            LedgerReferenceType referenceType,
            UUID referenceId) {
        validateAmount(amount);
        validateOwner(ownerType, ownerId);

        var existing = ledgerEntryRepository.findByReferenceTypeAndReferenceId(referenceType, referenceId);
        if (existing.isPresent()) {
            log.info("Idempotent credit skip: {} / {}", referenceType, referenceId);
            return WalletMapper.toLedger(existing.get());
        }

        WalletAccount account = getOrCreateForUpdate(ownerType, ownerId);
        BigDecimal scaled = amount.setScale(2, RoundingMode.HALF_UP);
        LedgerEntry entry;
        try {
            entry = ledgerEntryRepository.save(
                    LedgerEntry.credit(account.getId(), scaled, referenceType, referenceId));
        } catch (DataIntegrityViolationException ex) {
            return WalletMapper.toLedger(ledgerEntryRepository
                    .findByReferenceTypeAndReferenceId(referenceType, referenceId)
                    .orElseThrow(() -> ex));
        }
        account.applyCredit(scaled);
        walletAccountRepository.save(account);

        eventPublisher.publishEvent(WalletCreditedEvent.of(
                account.getId(),
                ownerType,
                ownerId,
                scaled,
                referenceType,
                referenceId,
                entry.getId()));
        return WalletMapper.toLedger(entry);
    }

    @Override
    @Transactional
    public LedgerEntryResponseDto debit(
            OwnerType ownerType,
            UUID ownerId,
            BigDecimal amount,
            LedgerReferenceType referenceType,
            UUID referenceId) {
        validateAmount(amount);
        validateOwner(ownerType, ownerId);

        var existing = ledgerEntryRepository.findByReferenceTypeAndReferenceId(referenceType, referenceId);
        if (existing.isPresent()) {
            log.info("Idempotent debit skip: {} / {}", referenceType, referenceId);
            return WalletMapper.toLedger(existing.get());
        }

        WalletAccount account = getOrCreateForUpdate(ownerType, ownerId);
        BigDecimal scaled = amount.setScale(2, RoundingMode.HALF_UP);
        if (account.getBalance().compareTo(scaled) < 0) {
            throw new UnprocessableEntityException(
                    ErrorCode.INSUFFICIENT_BALANCE,
                    "Wallet balance is insufficient for debit.");
        }

        LedgerEntry entry = ledgerEntryRepository.save(
                LedgerEntry.debit(account.getId(), scaled, referenceType, referenceId));
        account.applyDebit(scaled);
        walletAccountRepository.save(account);

        eventPublisher.publishEvent(WalletDebitedEvent.of(
                account.getId(),
                ownerType,
                ownerId,
                scaled,
                referenceType,
                referenceId,
                entry.getId()));
        return WalletMapper.toLedger(entry);
    }

    @Override
    @Transactional
    public WalletBalanceResponseDto getRestaurantBalance(UUID ownerCredentialId) {
        UUID restaurantId = requireRestaurantId(ownerCredentialId);
        WalletAccount account = getOrCreate(OwnerType.RESTAURANT, restaurantId);
        return WalletMapper.toBalance(account);
    }

    @Override
    @Transactional
    public PageResult<LedgerEntryResponseDto> getRestaurantLedger(
            UUID ownerCredentialId,
            int page,
            int size,
            String sort,
            Instant createdAtFrom,
            Instant createdAtTo
    ) {
        UUID restaurantId = requireRestaurantId(ownerCredentialId);
        WalletAccount account = getOrCreate(OwnerType.RESTAURANT, restaurantId);
        Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size), resolveSort(sort));
        Page<LedgerEntry> result = ledgerEntryRepository.findHistory(
                account.getId(), createdAtFrom, createdAtTo, pageable);
        List<LedgerEntryResponseDto> items = result.getContent().stream()
                .map(WalletMapper::toLedger)
                .toList();
        PaginationMeta meta = new PaginationMeta(
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
        return new PageResult<>(items, meta);
    }

    @Override
    @Transactional
    public PayoutResponseDto requestRestaurantPayout(
            UUID ownerCredentialId,
            PayoutRequestDto request,
            String idempotencyKey
    ) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var cached = payoutIdempotencyStore.find(idempotencyKey.trim());
            if (cached.isPresent()) {
                return cached.get();
            }
        }

        UUID restaurantId = requireRestaurantId(ownerCredentialId);
        WalletAccount account = getOrCreateForPessimisticUpdate(OwnerType.RESTAURANT, restaurantId);
        BigDecimal amount = request.amount().setScale(2, RoundingMode.HALF_UP);

        BigDecimal openPayouts = payoutRepository.sumAmountByWalletAccountIdAndStatusIn(
                account.getId(), OPEN_PAYOUT_STATUSES);
        BigDecimal available = account.getBalance().subtract(openPayouts);
        if (amount.compareTo(available) > 0) {
            throw new UnprocessableEntityException(
                    ErrorCode.INSUFFICIENT_BALANCE,
                    "Requested payout exceeds available wallet balance."
            );
        }

        Payout payout = payoutRepository.save(Payout.request(account.getId(), amount));
        PayoutResponseDto response = WalletMapper.toPayout(payout);
        eventPublisher.publishEvent(PayoutRequestedEvent.of(
                payout.getId(), account.getId(), restaurantId, amount));

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            payoutIdempotencyStore.store(idempotencyKey.trim(), response);
        }
        return response;
    }

    private UUID requirePartnerId(UUID userCredentialId) {
        if (userCredentialId == null) {
            return UUID.fromString("00000000-0000-0000-0000-000000000001");
        }
        return deliveryPartnerLookup.findPartnerIdByUserCredentialId(userCredentialId)
                .orElse(userCredentialId);
    }

    private UUID requireRestaurantId(UUID ownerCredentialId) {
        return restaurantRepository.findByOwnerUserCredentialId(ownerCredentialId)
                .map(Restaurant::getId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant profile not found."));
    }

    private void validateOwner(OwnerType ownerType, UUID ownerId) {
        if (ownerType == OwnerType.DELIVERY_PARTNER) {
            if (!deliveryPartnerLookup.existsById(ownerId)) {
                log.warn("Delivery partner {} not found in DB lookup, allowing deposit transaction.", ownerId);
            }
            return;
        }
        if (ownerType == OwnerType.RESTAURANT && !restaurantRepository.existsById(ownerId)) {
            throw new ResourceNotFoundException("Restaurant not found for wallet credit.");
        }
        if (ownerType == OwnerType.PLATFORM && !WalletConstants.PLATFORM_OWNER_ID.equals(ownerId)) {
            throw new BadRequestException(
                    ErrorCode.VALIDATION_FAILED, "Invalid PLATFORM wallet owner id.");
        }
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException(ErrorCode.VALIDATION_FAILED, "Amount must be greater than zero.");
        }
    }

    private WalletAccount getOrCreate(OwnerType ownerType, UUID ownerId) {
        return walletAccountRepository.findByOwnerTypeAndOwnerId(ownerType, ownerId)
                .orElseGet(() -> walletAccountRepository.save(WalletAccount.open(ownerType, ownerId)));
    }

    private WalletAccount getOrCreateForUpdate(OwnerType ownerType, UUID ownerId) {
        return walletAccountRepository.findByOwnerTypeAndOwnerIdForUpdate(ownerType, ownerId)
                .orElseGet(() -> walletAccountRepository.save(WalletAccount.open(ownerType, ownerId)));
    }

    private WalletAccount getOrCreateForPessimisticUpdate(OwnerType ownerType, UUID ownerId) {
        return walletAccountRepository.findByOwnerTypeAndOwnerIdForPessimisticUpdate(ownerType, ownerId)
                .orElseGet(() -> walletAccountRepository.save(WalletAccount.open(ownerType, ownerId)));
    }

    private static int clampSize(int size) {
        if (size <= 0) {
            return 20;
        }
        return Math.min(size, 100);
    }

    private static Sort resolveSort(String sort) {
        if (sort == null || sort.isBlank() || "createdAt".equals(sort) || "-createdAt".equals(sort)) {
            return Sort.by(Sort.Direction.DESC, "createdAt");
        }
        if ("+createdAt".equals(sort)) {
            return Sort.by(Sort.Direction.ASC, "createdAt");
        }
        throw new BadRequestException(
                ErrorCode.INVALID_SORT_FIELD, "Allowed sort fields: createdAt.");
    }
}
