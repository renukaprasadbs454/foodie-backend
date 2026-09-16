package com.foodie.payment.service.impl;

import com.foodie.common.enums.LedgerEntryType;
import com.foodie.common.enums.LedgerReferenceType;
import com.foodie.common.enums.OwnerType;
import com.foodie.common.exception.ResourceNotFoundException;
import com.foodie.order.entity.Order;
import com.foodie.order.repository.OrderRepository;
import com.foodie.payment.entity.OrderSettlement;
import com.foodie.payment.entity.Payment;
import com.foodie.payment.repository.OrderSettlementRepository;
import com.foodie.payment.service.SettlementService;
import com.foodie.wallet.entity.LedgerEntry;
import com.foodie.wallet.entity.WalletAccount;
import com.foodie.wallet.repository.LedgerEntryRepository;
import com.foodie.wallet.repository.WalletAccountRepository;
import com.foodie.wallet.service.impl.WalletAccountHelper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SettlementServiceImpl implements SettlementService {

    private static final Logger log = LoggerFactory.getLogger(SettlementServiceImpl.class);

    private static final BigDecimal RESTAURANT_COMMISSION_RATE = new BigDecimal("14.00");
    private static final BigDecimal DELIVERY_COMMISSION_RATE = new BigDecimal("10.00");
    private static final BigDecimal PLATFORM_FIXED_FEE = new BigDecimal("40.00");
    private static final UUID PLATFORM_OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private final OrderSettlementRepository settlementRepository;
    private final OrderRepository orderRepository;
    private final WalletAccountRepository walletAccountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final WalletAccountHelper walletAccountHelper;

    public SettlementServiceImpl(
            OrderSettlementRepository settlementRepository,
            OrderRepository orderRepository,
            WalletAccountRepository walletAccountRepository,
            LedgerEntryRepository ledgerEntryRepository,
            WalletAccountHelper walletAccountHelper) {
        this.settlementRepository = settlementRepository;
        this.orderRepository = orderRepository;
        this.walletAccountRepository = walletAccountRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.walletAccountHelper = walletAccountHelper;
    }

    @Override
    public SplitBreakdownDto calculateSplit(BigDecimal foodSubtotal, BigDecimal deliveryFee) {
        BigDecimal safeFood = foodSubtotal != null ? foodSubtotal.max(BigDecimal.ZERO) : BigDecimal.ZERO;
        BigDecimal safeDelivery = deliveryFee != null ? deliveryFee.max(BigDecimal.ZERO) : BigDecimal.ZERO;
        BigDecimal platformFee = PLATFORM_FIXED_FEE;

        BigDecimal restComm = safeFood.multiply(RESTAURANT_COMMISSION_RATE)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal restPayout = safeFood.subtract(restComm);

        BigDecimal delivComm = safeDelivery.multiply(DELIVERY_COMMISSION_RATE)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal delivPayout = safeDelivery.subtract(delivComm);

        BigDecimal adminEarnings = restComm.add(delivComm).add(platformFee);
        BigDecimal totalPaid = safeFood.add(safeDelivery).add(platformFee);

        return new SplitBreakdownDto(
                totalPaid,
                safeFood,
                safeDelivery,
                platformFee,
                RESTAURANT_COMMISSION_RATE,
                restComm,
                restPayout,
                DELIVERY_COMMISSION_RATE,
                delivComm,
                delivPayout,
                adminEarnings
        );
    }

    @Override
    @Transactional
    public OrderSettlement processPaymentSettlement(Payment payment) {
        Order order = orderRepository.findById(payment.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found for ID " + payment.getOrderId()));

        // Idempotency check: Return existing settlement if already processed for this order
        Optional<OrderSettlement> existing = settlementRepository.findByOrderId(order.getId());
        if (existing.isPresent()) {
            log.info("Settlement already processed for orderId={}", order.getId());
            return existing.get();
        }

        BigDecimal foodSubtotal = order.getSubtotal() != null ? order.getSubtotal() : BigDecimal.ZERO;
        BigDecimal deliveryFee = order.getDeliveryFee() != null ? order.getDeliveryFee() : BigDecimal.ZERO;
        BigDecimal taxAmount = order.getTaxAmount() != null ? order.getTaxAmount() : BigDecimal.ZERO;
        BigDecimal discountAmount = order.getDiscountAmount() != null ? order.getDiscountAmount() : BigDecimal.ZERO;
        BigDecimal totalPaid = order.getTotalAmount() != null ? order.getTotalAmount() : payment.getAmount();

        SplitBreakdownDto split = calculateSplit(foodSubtotal, deliveryFee);

        String txRef = payment.getCashfreeOrderId() != null ? payment.getCashfreeOrderId() : "TXN-" + order.getOrderNumber();

        OrderSettlement settlement = OrderSettlement.create(
                order.getId(),
                payment.getId(),
                order.getCustomerId(),
                order.getRestaurantId(),
                order.getDeliveryPartnerId(),
                totalPaid,
                foodSubtotal,
                deliveryFee,
                split.platformFee(),
                taxAmount,
                discountAmount,
                split.restaurantCommissionRate(),
                split.restaurantCommissionAmount(),
                split.restaurantPayout(),
                split.deliveryCommissionRate(),
                split.deliveryCommissionAmount(),
                split.deliveryPayout(),
                split.adminTotalEarnings(),
                txRef
        );

        settlement = settlementRepository.save(settlement);

        // Credit Wallet Balances & Post Immutable Ledger Entries for PLATFORM, RESTAURANT, DELIVERY_PARTNER
        creditWalletAndPostLedger(OwnerType.PLATFORM, PLATFORM_OWNER_ID, split.adminTotalEarnings(), order.getId());
        creditWalletAndPostLedger(OwnerType.RESTAURANT, order.getRestaurantId(), split.restaurantPayout(), order.getId());

        if (order.getDeliveryPartnerId() != null) {
            creditWalletAndPostLedger(OwnerType.DELIVERY_PARTNER, order.getDeliveryPartnerId(), split.deliveryPayout(), order.getId());
        }

        log.info("Successfully created settlement and distributed funds for orderId={}, settlementId={}", order.getId(), settlement.getId());
        return settlement;
    }

    private void creditWalletAndPostLedger(OwnerType ownerType, UUID ownerId, BigDecimal amount, UUID orderId) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        WalletAccount wallet = walletAccountRepository.findByOwnerTypeAndOwnerId(ownerType, ownerId)
                .orElseGet(() -> {
                    try {
                        return walletAccountHelper.createAccountSafely(ownerType, ownerId);
                    } catch (Exception ex) {
                        return walletAccountRepository.findByOwnerTypeAndOwnerId(ownerType, ownerId)
                                .orElseThrow(() -> new IllegalStateException("Failed to get or create wallet account for " + ownerType + ":" + ownerId, ex));
                    }
                });

        wallet.applyCredit(amount);
        walletAccountRepository.save(wallet);

        try {
            ledgerEntryRepository.save(LedgerEntry.credit(
                    wallet.getId(),
                    amount,
                    LedgerReferenceType.ORDER_EARNING,
                    orderId
            ));
        } catch (Exception ex) {
            log.debug("Ledger entry reference already recorded for orderId={}: {}", orderId, ex.getMessage());
        }
    }

    @Override
    public Optional<OrderSettlement> getByOrderId(UUID orderId) {
        return settlementRepository.findByOrderId(orderId);
    }

    @Override
    public Optional<OrderSettlement> getByPaymentId(UUID paymentId) {
        return settlementRepository.findByPaymentId(paymentId);
    }

    @Override
    public List<OrderSettlement> getByRestaurantId(UUID restaurantId) {
        return settlementRepository.findByRestaurantId(restaurantId);
    }

    @Override
    public List<OrderSettlement> getByDeliveryPartnerId(UUID deliveryPartnerId) {
        return settlementRepository.findByDeliveryPartnerId(deliveryPartnerId);
    }

    @Override
    public Page<OrderSettlement> listSettlements(UUID restaurantId, UUID deliveryPartnerId, String status, Pageable pageable) {
        return settlementRepository.search(restaurantId, deliveryPartnerId, status, pageable);
    }

    @Override
    @Transactional
    public void processRefundAdjustment(UUID paymentId, BigDecimal refundAmount, String reason) {
        Optional<OrderSettlement> found = settlementRepository.findByPaymentId(paymentId);
        if (found.isPresent()) {
            OrderSettlement settlement = found.get();
            settlement.markRefunded();
            settlementRepository.save(settlement);
            log.info("Settlement status marked REFUNDED for paymentId={}", paymentId);
        }
    }
}
