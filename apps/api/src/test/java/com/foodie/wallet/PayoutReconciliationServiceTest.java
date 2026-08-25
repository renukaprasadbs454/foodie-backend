package com.foodie.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import com.foodie.common.enums.PayoutStatus;
import com.foodie.common.enums.ReconciliationStatus;
import com.foodie.wallet.dto.request.ProviderPayoutRecordDto;
import com.foodie.wallet.dto.response.PayoutReconciliationResultDto;
import com.foodie.wallet.entity.Payout;
import com.foodie.wallet.repository.PayoutRepository;
import com.foodie.wallet.service.PayoutReconciliationService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PayoutReconciliationServiceTest {

    @Mock
    private PayoutRepository payoutRepository;

    private PayoutReconciliationService reconciliationService;

    @BeforeEach
    void setUp() {
        reconciliationService = new PayoutReconciliationService(payoutRepository);
    }

    @Test
    void reconcile_matchedRecords() {
        UUID walletAccountId = UUID.randomUUID();
        Payout payout = Payout.request(walletAccountId, new BigDecimal("100.00"));
        payout.complete("BANK999");

        ProviderPayoutRecordDto providerRecord = new ProviderPayoutRecordDto(
                payout.getId(),
                new BigDecimal("100.00"),
                "SUCCESS",
                "BANK999",
                Instant.now()
        );

        when(payoutRepository.findAllById(anyList())).thenReturn(List.of(payout));

        PayoutReconciliationResultDto result = reconciliationService.reconcile(List.of(providerRecord));

        assertThat(result.totalEvaluated()).isEqualTo(1);
        assertThat(result.matchedCount()).isEqualTo(1);
        assertThat(result.mismatchCount()).isEqualTo(0);
        assertThat(result.items().getFirst().reconciliationStatus()).isEqualTo(ReconciliationStatus.MATCHED);
    }

    @Test
    void reconcile_amountMismatch() {
        UUID walletAccountId = UUID.randomUUID();
        Payout payout = Payout.request(walletAccountId, new BigDecimal("100.00"));
        payout.complete("BANK999");

        ProviderPayoutRecordDto providerRecord = new ProviderPayoutRecordDto(
                payout.getId(),
                new BigDecimal("150.00"), // Mismatched amount
                "SUCCESS",
                "BANK999",
                Instant.now()
        );

        when(payoutRepository.findAllById(anyList())).thenReturn(List.of(payout));

        PayoutReconciliationResultDto result = reconciliationService.reconcile(List.of(providerRecord));

        assertThat(result.matchedCount()).isEqualTo(0);
        assertThat(result.mismatchCount()).isEqualTo(1);
        assertThat(result.items().getFirst().reconciliationStatus()).isEqualTo(ReconciliationStatus.AMOUNT_MISMATCH);
    }

    @Test
    void reconcile_statusMismatch() {
        UUID walletAccountId = UUID.randomUUID();
        Payout payout = Payout.request(walletAccountId, new BigDecimal("100.00"));
        payout.complete("BANK999");

        ProviderPayoutRecordDto providerRecord = new ProviderPayoutRecordDto(
                payout.getId(),
                new BigDecimal("100.00"),
                "FAILED", // Mismatched status
                "BANK999",
                Instant.now()
        );

        when(payoutRepository.findAllById(anyList())).thenReturn(List.of(payout));

        PayoutReconciliationResultDto result = reconciliationService.reconcile(List.of(providerRecord));

        assertThat(result.mismatchCount()).isEqualTo(1);
        assertThat(result.items().getFirst().reconciliationStatus()).isEqualTo(ReconciliationStatus.STATUS_MISMATCH);
    }

    @Test
    void reconcile_missingProviderRecord() {
        UUID randomPayoutId = UUID.randomUUID();

        ProviderPayoutRecordDto providerRecord = new ProviderPayoutRecordDto(
                randomPayoutId,
                new BigDecimal("100.00"),
                "SUCCESS",
                "BANK999",
                Instant.now()
        );

        when(payoutRepository.findAllById(anyList())).thenReturn(List.of());

        PayoutReconciliationResultDto result = reconciliationService.reconcile(List.of(providerRecord));

        assertThat(result.mismatchCount()).isEqualTo(1);
        assertThat(result.items().getFirst().reconciliationStatus()).isEqualTo(ReconciliationStatus.MISSING_PROVIDER_RECORD);
    }
}
