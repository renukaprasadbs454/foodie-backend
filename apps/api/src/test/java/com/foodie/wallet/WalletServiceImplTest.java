package com.foodie.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.foodie.common.enums.LedgerEntryType;
import com.foodie.common.enums.LedgerReferenceType;
import com.foodie.common.enums.OwnerType;
import com.foodie.common.enums.PayoutStatus;
import com.foodie.common.exception.BadRequestException;
import com.foodie.common.exception.ErrorCode;
import com.foodie.common.exception.UnprocessableEntityException;
import com.foodie.restaurant.repository.RestaurantRepository;
import com.foodie.shared.contract.DeliveryPartnerLookup;
import com.foodie.shared.event.PayoutRequestedEvent;
import com.foodie.shared.event.WalletCreditedEvent;
import com.foodie.wallet.dto.request.PayoutRequestDto;
import com.foodie.wallet.dto.response.LedgerEntryResponseDto;
import com.foodie.wallet.dto.response.PayoutResponseDto;
import com.foodie.wallet.dto.response.WalletBalanceResponseDto;
import com.foodie.wallet.entity.LedgerEntry;
import com.foodie.wallet.entity.Payout;
import com.foodie.wallet.entity.WalletAccount;
import com.foodie.wallet.repository.LedgerEntryRepository;
import com.foodie.wallet.repository.PayoutRepository;
import com.foodie.wallet.repository.WalletAccountRepository;
import com.foodie.wallet.service.PayoutIdempotencyStore;
import com.foodie.wallet.service.impl.WalletServiceImpl;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class WalletServiceImplTest {

    @Mock private WalletAccountRepository walletAccountRepository;
    @Mock private LedgerEntryRepository ledgerEntryRepository;
    @Mock private PayoutRepository payoutRepository;
    @Mock private DeliveryPartnerLookup deliveryPartnerLookup;
    @Mock private RestaurantRepository restaurantRepository;
    @Mock private PayoutIdempotencyStore payoutIdempotencyStore;
    @Mock private ApplicationEventPublisher eventPublisher;

    private WalletServiceImpl service;

    private final UUID credentialId = UUID.randomUUID();
    private final UUID partnerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new WalletServiceImpl(
                walletAccountRepository,
                ledgerEntryRepository,
                payoutRepository,
                deliveryPartnerLookup,
                restaurantRepository,
                payoutIdempotencyStore,
                eventPublisher
        );
    }

        @Test
        void getBalance_returnsCachedBalance() {
                WalletAccount account = WalletAccount.open(OwnerType.DELIVERY_PARTNER, partnerId);
                account.applyCredit(new BigDecimal("120.50"));
                when(deliveryPartnerLookup.findPartnerIdByUserCredentialId(credentialId))
                                .thenReturn(Optional.of(partnerId));
                when(walletAccountRepository.findByOwnerTypeAndOwnerId(OwnerType.DELIVERY_PARTNER, partnerId))
                                .thenReturn(Optional.of(account));
                when(payoutRepository.sumAmountByWalletAccountIdAndStatusIn(eq(account.getId()), any()))
                                .thenReturn(new BigDecimal("20.00"));
                when(ledgerEntryRepository.sumCreditAmountByWalletAccountId(account.getId()))
                                .thenReturn(new BigDecimal("200.00"));
                when(payoutRepository.sumCompletedAmountByWalletAccountId(account.getId()))
                                .thenReturn(new BigDecimal("79.50"));

                WalletBalanceResponseDto balance = service.getBalance(credentialId);

                assertThat(balance.balance()).isEqualByComparingTo("120.50");
                assertThat(balance.availableBalance()).isEqualByComparingTo("100.50");
                assertThat(balance.pendingBalance()).isEqualByComparingTo("20.00");
                assertThat(balance.totalEarnings()).isEqualByComparingTo("200.00");
                assertThat(balance.totalPayouts()).isEqualByComparingTo("79.50");
                assertThat(balance.walletAccountId()).isEqualTo(account.getId());
        }

        @Test
        void credit_appendsLedgerAndUpdatesCache() {
                WalletAccount account = WalletAccount.open(OwnerType.DELIVERY_PARTNER, partnerId);
                UUID assignmentId = UUID.randomUUID();
                when(deliveryPartnerLookup.existsById(partnerId)).thenReturn(true);
                when(ledgerEntryRepository.findByReferenceTypeAndReferenceId(
                                LedgerReferenceType.DELIVERY_ASSIGNMENT, assignmentId))
                                .thenReturn(Optional.empty());
                when(walletAccountRepository.findByOwnerTypeAndOwnerIdForUpdate(
                                OwnerType.DELIVERY_PARTNER, partnerId))
                                .thenReturn(Optional.of(account));
                when(ledgerEntryRepository.save(any(LedgerEntry.class))).thenAnswer(inv -> inv.getArgument(0));
                when(walletAccountRepository.save(any(WalletAccount.class))).thenAnswer(inv -> inv.getArgument(0));

                LedgerEntryResponseDto result = service.credit(
                                OwnerType.DELIVERY_PARTNER,
                                partnerId,
                                new BigDecimal("30.00"),
                                LedgerReferenceType.DELIVERY_ASSIGNMENT,
                                assignmentId);

                assertThat(result.entryType()).isEqualTo(LedgerEntryType.CREDIT);
                assertThat(result.amount()).isEqualByComparingTo("30.00");
                assertThat(account.getBalance()).isEqualByComparingTo("30.00");
                verify(eventPublisher).publishEvent(any(WalletCreditedEvent.class));
        }

        @Test
        void credit_idempotentOnSameReference() {
                UUID assignmentId = UUID.randomUUID();
                WalletAccount account = WalletAccount.open(OwnerType.DELIVERY_PARTNER, partnerId);
                LedgerEntry existing = LedgerEntry.credit(
                                account.getId(), new BigDecimal("30.00"),
                                LedgerReferenceType.DELIVERY_ASSIGNMENT, assignmentId);
                when(deliveryPartnerLookup.existsById(partnerId)).thenReturn(true);
                when(ledgerEntryRepository.findByReferenceTypeAndReferenceId(
                                LedgerReferenceType.DELIVERY_ASSIGNMENT, assignmentId))
                                .thenReturn(Optional.of(existing));

                LedgerEntryResponseDto result = service.credit(
                                OwnerType.DELIVERY_PARTNER,
                                partnerId,
                                new BigDecimal("30.00"),
                                LedgerReferenceType.DELIVERY_ASSIGNMENT,
                                assignmentId);

                assertThat(result.amount()).isEqualByComparingTo("30.00");
                verify(ledgerEntryRepository, never()).save(any());
                verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        void credit_platformRefund() {
                UUID refundId = UUID.randomUUID();
                WalletAccount platform = WalletAccount.open(OwnerType.PLATFORM, WalletConstants.PLATFORM_OWNER_ID);
                when(ledgerEntryRepository.findByReferenceTypeAndReferenceId(LedgerReferenceType.REFUND, refundId))
                                .thenReturn(Optional.empty());
                when(walletAccountRepository.findByOwnerTypeAndOwnerIdForUpdate(
                                OwnerType.PLATFORM, WalletConstants.PLATFORM_OWNER_ID))
                                .thenReturn(Optional.of(platform));
                when(ledgerEntryRepository.save(any(LedgerEntry.class))).thenAnswer(inv -> inv.getArgument(0));
                when(walletAccountRepository.save(any(WalletAccount.class))).thenAnswer(inv -> inv.getArgument(0));

                LedgerEntryResponseDto result = service.credit(
                                OwnerType.PLATFORM,
                                WalletConstants.PLATFORM_OWNER_ID,
                                new BigDecimal("99.00"),
                                LedgerReferenceType.REFUND,
                                refundId);

                assertThat(result.referenceType()).isEqualTo(LedgerReferenceType.REFUND);
                assertThat(platform.getBalance()).isEqualByComparingTo("99.00");
        }

        @Test
        void requestPayout_insufficientBalance_throws422() {
                WalletAccount account = WalletAccount.open(OwnerType.DELIVERY_PARTNER, partnerId);
                account.applyCredit(new BigDecimal("10.00"));
                when(deliveryPartnerLookup.findPartnerIdByUserCredentialId(credentialId))
                                .thenReturn(Optional.of(partnerId));
                when(walletAccountRepository.findByOwnerTypeAndOwnerIdForPessimisticUpdate(
                                OwnerType.DELIVERY_PARTNER, partnerId))
                                .thenReturn(Optional.of(account));
                when(payoutRepository.sumAmountByWalletAccountIdAndStatusIn(eq(account.getId()), any()))
                                .thenReturn(BigDecimal.ZERO);

                assertThatThrownBy(() -> service.requestPayout(
                                credentialId,
                                new PayoutRequestDto(new BigDecimal("50.00"), "John Doe", "1234567890", "IFSC0001234",
                                                "Bank"),
                                null))
                                .isInstanceOf(UnprocessableEntityException.class)
                                .extracting(ex -> ((UnprocessableEntityException) ex).getErrorCode())
                                .isEqualTo(ErrorCode.INSUFFICIENT_BALANCE);
                verify(payoutRepository, never()).save(any());
        }

        @Test
        void requestPayout_createsRequestedWithoutLedgerDebit() {
                WalletAccount account = WalletAccount.open(OwnerType.DELIVERY_PARTNER, partnerId);
                account.applyCredit(new BigDecimal("100.00"));
                when(deliveryPartnerLookup.findPartnerIdByUserCredentialId(credentialId))
                                .thenReturn(Optional.of(partnerId));
                when(walletAccountRepository.findByOwnerTypeAndOwnerIdForPessimisticUpdate(
                                OwnerType.DELIVERY_PARTNER, partnerId))
                                .thenReturn(Optional.of(account));
                when(payoutRepository.sumAmountByWalletAccountIdAndStatusIn(eq(account.getId()), any()))
                                .thenReturn(BigDecimal.ZERO);
                when(payoutRepository.save(any(Payout.class))).thenAnswer(inv -> inv.getArgument(0));

                PayoutResponseDto response = service.requestPayout(
                                credentialId, new PayoutRequestDto(new BigDecimal("40.00"), "John Doe", "1234567890",
                                                "IFSC0001234", "Bank"),
                                "pay-key-1");

                assertThat(response.status()).isEqualTo(PayoutStatus.REQUESTED);
                assertThat(response.amount()).isEqualByComparingTo("40.00");
                assertThat(account.getBalance()).isEqualByComparingTo("100.00");
                verify(ledgerEntryRepository, never()).save(any());
                verify(eventPublisher).publishEvent(any(PayoutRequestedEvent.class));
                verify(payoutIdempotencyStore).store(eq("pay-key-1"), any());
        }

        @Test
        void getLedger_invalidSort_throws400() {
                when(deliveryPartnerLookup.findPartnerIdByUserCredentialId(credentialId))
                                .thenReturn(Optional.of(partnerId));
                when(walletAccountRepository.findByOwnerTypeAndOwnerId(OwnerType.DELIVERY_PARTNER, partnerId))
                                .thenReturn(Optional.of(WalletAccount.open(OwnerType.DELIVERY_PARTNER, partnerId)));

                assertThatThrownBy(() -> service.getLedger(credentialId, 0, 20, "amount", null, null))
                                .isInstanceOf(BadRequestException.class)
                                .extracting(ex -> ((BadRequestException) ex).getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_SORT_FIELD);
        }

        @Test
        void getLedger_filtersAndPages() {
                WalletAccount account = WalletAccount.open(OwnerType.DELIVERY_PARTNER, partnerId);
                LedgerEntry entry = LedgerEntry.credit(
                                account.getId(),
                                new BigDecimal("30.00"),
                                LedgerReferenceType.DELIVERY_ASSIGNMENT,
                                UUID.randomUUID());
                Instant from = Instant.now().minusSeconds(3600);
                Instant to = Instant.now();
                when(deliveryPartnerLookup.findPartnerIdByUserCredentialId(credentialId))
                                .thenReturn(Optional.of(partnerId));
                when(walletAccountRepository.findByOwnerTypeAndOwnerId(OwnerType.DELIVERY_PARTNER, partnerId))
                                .thenReturn(Optional.of(account));
                when(ledgerEntryRepository.findHistory(eq(account.getId()), eq(from), eq(to), any(Pageable.class)))
                                .thenReturn(new PageImpl<>(List.of(entry)));

                var page = service.getLedger(credentialId, 0, 20, "createdAt", from, to);

                assertThat(page.items()).hasSize(1);
                assertThat(page.items().getFirst().entryType()).isEqualTo(LedgerEntryType.CREDIT);
                ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
                verify(ledgerEntryRepository).findHistory(eq(account.getId()), eq(from), eq(to), pageableCaptor.capture());
                assertThat(pageableCaptor.getValue().getSort().getOrderFor("createdAt").getDirection())
                                .isEqualTo(org.springframework.data.domain.Sort.Direction.DESC);
        }

        @Test
        void getPayoutHistory_returnsPaginatedPayouts() {
                WalletAccount account = WalletAccount.open(OwnerType.DELIVERY_PARTNER, partnerId);
                Payout payout = Payout.request(account.getId(), new BigDecimal("50.00"));
                when(deliveryPartnerLookup.findPartnerIdByUserCredentialId(credentialId))
                                .thenReturn(Optional.of(partnerId));
                when(walletAccountRepository.findByOwnerTypeAndOwnerId(OwnerType.DELIVERY_PARTNER, partnerId))
                                .thenReturn(Optional.of(account));
                when(payoutRepository.findByWalletAccountId(eq(account.getId()), any(Pageable.class)))
                                .thenReturn(new PageImpl<>(List.of(payout)));

                var result = service.getPayoutHistory(credentialId, 0, 20);

                assertThat(result.items()).hasSize(1);
                assertThat(result.items().getFirst().amount()).isEqualByComparingTo("50.00");
        }

        @Test
        void getPayoutDetail_returnsPayoutDetails() {
                WalletAccount account = WalletAccount.open(OwnerType.DELIVERY_PARTNER, partnerId);
                Payout payout = Payout.request(account.getId(), new BigDecimal("75.00"));
                when(deliveryPartnerLookup.findPartnerIdByUserCredentialId(credentialId))
                                .thenReturn(Optional.of(partnerId));
                when(walletAccountRepository.findByOwnerTypeAndOwnerId(OwnerType.DELIVERY_PARTNER, partnerId))
                                .thenReturn(Optional.of(account));
                when(payoutRepository.findByIdAndWalletAccountId(payout.getId(), account.getId()))
                                .thenReturn(Optional.of(payout));

                PayoutResponseDto response = service.getPayoutDetail(credentialId, payout.getId());

                assertThat(response.payoutId()).isEqualTo(payout.getId());
                assertThat(response.amount()).isEqualByComparingTo("75.00");
        }

        @Test
        void updatePayoutStatus_completed_finalizesDebit() {
                WalletAccount account = WalletAccount.open(OwnerType.DELIVERY_PARTNER, partnerId);
                account.applyCredit(new BigDecimal("100.00"));
                Payout payout = Payout.request(account.getId(), new BigDecimal("40.00"));

                when(deliveryPartnerLookup.existsById(partnerId)).thenReturn(true);
                when(payoutRepository.findById(payout.getId())).thenReturn(Optional.of(payout));
                when(walletAccountRepository.findById(account.getId())).thenReturn(Optional.of(account));
                when(walletAccountRepository.findByOwnerTypeAndOwnerIdForPessimisticUpdate(
                                OwnerType.DELIVERY_PARTNER, partnerId))
                                .thenReturn(Optional.of(account));
                when(walletAccountRepository.findByOwnerTypeAndOwnerIdForUpdate(
                                OwnerType.DELIVERY_PARTNER, partnerId))
                                .thenReturn(Optional.of(account));
                when(payoutRepository.save(any(Payout.class))).thenAnswer(inv -> inv.getArgument(0));
                when(ledgerEntryRepository.save(any(LedgerEntry.class))).thenAnswer(inv -> inv.getArgument(0));

                PayoutResponseDto response = service.updatePayoutStatus(
                                payout.getId(), PayoutStatus.COMPLETED, "BANK12345", null);

                assertThat(response.status()).isEqualTo(PayoutStatus.COMPLETED);
                assertThat(account.getBalance()).isEqualByComparingTo("60.00");
                verify(ledgerEntryRepository).save(any(LedgerEntry.class));
        }

        @Test
        void updatePayoutStatus_failed_releasesReservedAmountWithoutDebit() {
                WalletAccount account = WalletAccount.open(OwnerType.DELIVERY_PARTNER, partnerId);
                account.applyCredit(new BigDecimal("100.00"));
                Payout payout = Payout.request(account.getId(), new BigDecimal("40.00"));

                when(payoutRepository.findById(payout.getId())).thenReturn(Optional.of(payout));
                when(walletAccountRepository.findById(account.getId())).thenReturn(Optional.of(account));
                when(walletAccountRepository.findByOwnerTypeAndOwnerIdForPessimisticUpdate(
                                OwnerType.DELIVERY_PARTNER, partnerId))
                                .thenReturn(Optional.of(account));
                when(payoutRepository.save(any(Payout.class))).thenAnswer(inv -> inv.getArgument(0));

                PayoutResponseDto response = service.updatePayoutStatus(
                                payout.getId(), PayoutStatus.FAILED, null, "Account invalid");

                assertThat(response.status()).isEqualTo(PayoutStatus.FAILED);
                assertThat(account.getBalance()).isEqualByComparingTo("100.00");
                verify(ledgerEntryRepository, never()).save(any());
        }
}
