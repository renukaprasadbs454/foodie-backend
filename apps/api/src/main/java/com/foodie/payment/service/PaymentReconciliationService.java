package com.foodie.payment.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface PaymentReconciliationService {

    ReconciliationReportDto runReconciliation();

    record ReconciliationReportDto(
            Instant executedAt,
            long totalPaymentsAudited,
            long matchedPaymentsCount,
            long mismatchedStatusCount,
            long pendingSettlementsCount,
            BigDecimal totalAuditedAmount,
            BigDecimal totalCommissionCalculated,
            BigDecimal totalRestaurantPayable,
            BigDecimal totalDeliveryPartnerPayable,
            List<ReconciliationIssueDto> issues,
            String gatewayAccountStatus
    ) {}

    record ReconciliationIssueDto(
            String paymentId,
            String orderId,
            String cashfreeOrderId,
            String dbStatus,
            String cashfreeStatus,
            String issueDescription,
            String severity
    ) {}
}
