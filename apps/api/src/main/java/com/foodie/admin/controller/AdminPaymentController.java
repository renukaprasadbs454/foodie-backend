package com.foodie.admin.controller;

import com.foodie.admin.dto.request.BulkApprovePayoutsRequestDto;
import com.foodie.admin.dto.request.CommissionConfigDto;
import com.foodie.admin.dto.response.AdminPayoutResponseDto;
import com.foodie.admin.dto.response.PaymentSettlementResponseDto;
import com.foodie.admin.dto.response.PaymentSplitBreakdownDto;
import com.foodie.admin.service.AdminPaymentService;
import com.foodie.payment.entity.Payment;
import com.foodie.payment.repository.PaymentRepository;
import com.foodie.wallet.entity.LedgerEntry;
import com.foodie.wallet.repository.LedgerEntryRepository;
import com.foodie.common.dto.ApiResponse;
import com.foodie.common.enums.OwnerType;
import com.foodie.payout.service.PayoutProcessingService;
import com.foodie.shared.contract.DeliveryPartnerLookup;
import com.foodie.shared.contract.RestaurantSummaryProvider;
import com.foodie.wallet.entity.Payout;
import com.foodie.wallet.entity.WalletAccount;
import com.foodie.wallet.repository.PayoutRepository;
import com.foodie.wallet.repository.WalletAccountRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/payments")
@Tag(name = "Admin — Payments & Settlement")
public class AdminPaymentController {

    private final AdminPaymentService adminPaymentService;
    private final com.foodie.restaurant.service.RestaurantSettlementService restaurantSettlementService;
    private final PayoutRepository payoutRepository;
    private final PaymentRepository paymentRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final com.foodie.payment.service.PaymentReconciliationService reconciliationService;
    private final com.foodie.payment.scheduler.WeeklySettlementScheduler weeklySettlementScheduler;
    private final WalletAccountRepository walletAccountRepository;
    private final RestaurantSummaryProvider restaurantSummaryProvider;
    private final DeliveryPartnerLookup deliveryPartnerLookup;
    private final PayoutProcessingService payoutProcessingService;

    public AdminPaymentController(
            AdminPaymentService adminPaymentService,
            com.foodie.restaurant.service.RestaurantSettlementService restaurantSettlementService,
            PayoutRepository payoutRepository,
            PaymentRepository paymentRepository,
            LedgerEntryRepository ledgerEntryRepository,
            com.foodie.payment.service.PaymentReconciliationService reconciliationService,
            com.foodie.payment.scheduler.WeeklySettlementScheduler weeklySettlementScheduler,
            WalletAccountRepository walletAccountRepository,
            RestaurantSummaryProvider restaurantSummaryProvider,
            DeliveryPartnerLookup deliveryPartnerLookup,
            PayoutProcessingService payoutProcessingService) {
        this.adminPaymentService = adminPaymentService;
        this.restaurantSettlementService = restaurantSettlementService;
        this.payoutRepository = payoutRepository;
        this.paymentRepository = paymentRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.reconciliationService = reconciliationService;
        this.weeklySettlementScheduler = weeklySettlementScheduler;
        this.walletAccountRepository = walletAccountRepository;
        this.restaurantSummaryProvider = restaurantSummaryProvider;
        this.deliveryPartnerLookup = deliveryPartnerLookup;
        this.payoutProcessingService = payoutProcessingService;
    }

    @GetMapping("/settlements")
    @PreAuthorize("hasRole('ADMIN') and @adminAccess.hasAnyRole(authentication, 'FINANCE', 'OPS', 'SUPER_ADMIN')")
    @Operation(summary = "List payment settlements with admin escrow & split breakdown")
    public ResponseEntity<ApiResponse<List<PaymentSettlementResponseDto>>> listSettlements() {
        return ResponseEntity.ok(ApiResponse.success(adminPaymentService.listSettlements()));
    }

    @GetMapping("/transactions")
    @PreAuthorize("hasRole('ADMIN') and @adminAccess.hasAnyRole(authentication, 'FINANCE', 'OPS', 'SUPER_ADMIN')")
    @Operation(summary = "List real payment transactions from database")
    public ResponseEntity<ApiResponse<List<Payment>>> listTransactions() {
        return ResponseEntity.ok(ApiResponse.success(paymentRepository.findAll()));
    }

    @GetMapping("/ledger")
    @PreAuthorize("hasRole('ADMIN') and @adminAccess.hasAnyRole(authentication, 'FINANCE', 'OPS', 'SUPER_ADMIN')")
    @Operation(summary = "List authoritative double-entry financial ledger records")
    public ResponseEntity<ApiResponse<List<LedgerEntry>>> listLedger() {
        return ResponseEntity.ok(ApiResponse.success(ledgerEntryRepository.findAll()));
    }

    @GetMapping("/restaurant-settlements")
    @PreAuthorize("hasRole('ADMIN') and @adminAccess.hasAnyRole(authentication, 'FINANCE', 'OPS', 'SUPER_ADMIN')")
    @Operation(summary = "List restaurant settlements for admin review")
    public ResponseEntity<ApiResponse<List<com.foodie.restaurant.dto.response.RestaurantSettlementResponseDto>>> listRestaurantSettlements(
            @RequestParam(required = false) java.util.UUID restaurantId,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(ApiResponse.success(
                restaurantSettlementService.getAllSettlementsForAdmin(restaurantId, status)));
    }

    @PostMapping("/restaurant-settlements/disburse")
    @PreAuthorize("hasRole('ADMIN') and @adminAccess.hasAnyRole(authentication, 'FINANCE', 'SUPER_ADMIN')")
    @Operation(summary = "Disburse payment to restaurant with transaction reference")
    public ResponseEntity<ApiResponse<com.foodie.restaurant.dto.response.RestaurantSettlementResponseDto>> disburseRestaurantSettlement(
            @Valid @RequestBody com.foodie.restaurant.dto.request.DisburseSettlementRequestDto request) {
        return ResponseEntity.ok(ApiResponse.success(
                restaurantSettlementService.disburseSettlement(request.settlementId(), request.paymentReference())));
    }

    @GetMapping({"/payouts", "/delivery-payouts"})
    @PreAuthorize("hasRole('ADMIN') and @adminAccess.hasAnyRole(authentication, 'FINANCE', 'OPS', 'SUPER_ADMIN')")
    @Operation(summary = "List payouts optionally filtered by partner type")
    public ResponseEntity<ApiResponse<List<AdminPayoutResponseDto>>> listPayouts(
            @RequestParam(required = false) OwnerType ownerType) {
        List<Payout> payouts;
        if (ownerType != null) {
            payouts = payoutRepository.findByOwnerType(ownerType);
        } else {
            payouts = payoutRepository.findAll();
        }

        List<AdminPayoutResponseDto> dtos = payouts.stream().map(p -> {
            String ownerName = p.getAccountHolderName();
            WalletAccount acc = walletAccountRepository.findById(p.getWalletAccountId()).orElse(null);
            if (acc != null) {
                if (acc.getOwnerType() == OwnerType.RESTAURANT) {
                    ownerName = restaurantSummaryProvider.findByRestaurantId(acc.getOwnerId())
                            .map(RestaurantSummaryProvider.RestaurantSummary::name)
                            .orElse(ownerName);
                } else if (acc.getOwnerType() == OwnerType.DELIVERY_PARTNER) {
                    ownerName = deliveryPartnerLookup.findPartnerNameById(acc.getOwnerId())
                            .orElse(ownerName != null && !ownerName.isBlank() ? ownerName : "Delivery Partner");
                }
            }
            return new AdminPayoutResponseDto(
                    p.getId(),
                    p.getWalletAccountId(),
                    p.getAmount(),
                    p.getStatus().name(),
                    p.getAccountHolderName(),
                    p.getAccountNumber(),
                    p.getIfscCode(),
                    p.getBankName(),
                    p.getProvider(),
                    p.getProviderPayoutId(),
                    p.getProviderReferenceId(),
                    p.getProviderStatus(),
                    p.getBankRef(),
                    p.getFailureReason(),
                    p.getProcessedAt(),
                    p.getCompletedAt(),
                    p.getCreatedAt(),
                    p.getUpdatedAt(),
                    ownerName);
        }).toList();

        return ResponseEntity.ok(ApiResponse.success(dtos));
    }

    @PostMapping({"/payouts/approve", "/delivery-payouts/approve"})
    @PreAuthorize("hasRole('ADMIN') and @adminAccess.hasAnyRole(authentication, 'FINANCE', 'SUPER_ADMIN')")
    @Operation(summary = "Bulk approve payouts and automatically disburse using active provider")
    public ResponseEntity<ApiResponse<String>> approvePayouts(
            @Valid @RequestBody BulkApprovePayoutsRequestDto request) {
        for (java.util.UUID payoutId : request.payoutIds()) {
            payoutProcessingService.processPayout(payoutId, java.util.UUID.randomUUID().toString());
        }
        return ResponseEntity
                .ok(ApiResponse.success("Approved and processing " + request.payoutIds().size() + " payouts."));
    }

    @PostMapping({"/payouts/{id}/approve", "/delivery-payouts/{id}/approve"})
    @PreAuthorize("hasRole('ADMIN') and @adminAccess.hasAnyRole(authentication, 'FINANCE', 'SUPER_ADMIN')")
    @Operation(summary = "Approve single payout request")
    public ResponseEntity<ApiResponse<String>> approveSinglePayout(
            @PathVariable("id") java.util.UUID payoutId) {
        payoutProcessingService.processPayout(payoutId, java.util.UUID.randomUUID().toString());
        return ResponseEntity.ok(ApiResponse.success("Payout " + payoutId + " approved successfully."));
    }

    @PostMapping({"/payouts/reject", "/delivery-payouts/reject"})
    @PreAuthorize("hasRole('ADMIN') and @adminAccess.hasAnyRole(authentication, 'FINANCE', 'SUPER_ADMIN')")
    @Operation(summary = "Reject a pending payout request")
    public ResponseEntity<ApiResponse<String>> rejectPayout(
            @RequestBody java.util.Map<String, Object> request) {
        String payoutIdStr = (String) request.get("payoutId");
        String reason = (String) request.getOrDefault("reason", "Rejected by Admin");
        if (payoutIdStr == null || payoutIdStr.isBlank()) {
            throw new com.foodie.common.exception.BadRequestException(
                    com.foodie.common.exception.ErrorCode.VALIDATION_FAILED, "payoutId is required.");
        }
        java.util.UUID payoutId = java.util.UUID.fromString(payoutIdStr);
        Payout payout = payoutRepository.findById(payoutId)
                .orElseThrow(() -> new com.foodie.common.exception.ResourceNotFoundException("Payout not found: " + payoutId));
        payout.markFailed(reason, "REJECTED_BY_ADMIN");
        payoutRepository.save(payout);
        return ResponseEntity.ok(ApiResponse.success("Payout " + payoutId + " has been rejected."));
    }

    @PostMapping({"/payouts/{id}/reject", "/delivery-payouts/{id}/reject"})
    @PreAuthorize("hasRole('ADMIN') and @adminAccess.hasAnyRole(authentication, 'FINANCE', 'SUPER_ADMIN')")
    @Operation(summary = "Reject single payout request by ID path param")
    public ResponseEntity<ApiResponse<String>> rejectSinglePayout(
            @PathVariable("id") java.util.UUID payoutId,
            @RequestBody(required = false) java.util.Map<String, Object> request) {
        String reason = (request != null && request.containsKey("reason"))
                ? (String) request.get("reason")
                : "Rejected by Admin";
        Payout payout = payoutRepository.findById(payoutId)
                .orElseThrow(() -> new com.foodie.common.exception.ResourceNotFoundException("Payout not found: " + payoutId));
        payout.markFailed(reason, "REJECTED_BY_ADMIN");
        payoutRepository.save(payout);
        return ResponseEntity.ok(ApiResponse.success("Payout " + payoutId + " has been rejected."));
    }

    @GetMapping("/commission-rules")
    @PreAuthorize("hasRole('ADMIN') and @adminAccess.hasAnyRole(authentication, 'FINANCE', 'OPS', 'SUPER_ADMIN')")
    @Operation(summary = "Get active commission and platform fee rules")
    public ResponseEntity<ApiResponse<CommissionConfigDto>> getCommissionRules() {
        return ResponseEntity.ok(ApiResponse.success(adminPaymentService.getCommissionRules()));
    }

    @PostMapping("/commission-rules")
    @PreAuthorize("hasRole('ADMIN') and @adminAccess.hasAnyRole(authentication, 'FINANCE', 'SUPER_ADMIN')")
    @Operation(summary = "Update active commission and platform fee rules")
    public ResponseEntity<ApiResponse<CommissionConfigDto>> updateCommissionRules(
            @Valid @RequestBody CommissionConfigDto config) {
        return ResponseEntity.ok(ApiResponse.success(adminPaymentService.updateCommissionRules(config)));
    }

    @PostMapping("/calculate-split")
    @PreAuthorize("hasRole('ADMIN') and @adminAccess.hasAnyRole(authentication, 'FINANCE', 'OPS', 'SUPER_ADMIN')")
    @Operation(summary = "Calculate payment split breakdown for given subtotal and delivery fee")
    public ResponseEntity<ApiResponse<PaymentSplitBreakdownDto>> calculateSplit(
            @RequestParam(defaultValue = "0.00") BigDecimal foodSubtotal,
            @RequestParam(defaultValue = "0.00") BigDecimal deliveryFee) {
        return ResponseEntity.ok(ApiResponse.success(
                adminPaymentService.calculateSplit(foodSubtotal, deliveryFee)));
    }

    @GetMapping("/reconciliation")
    @PreAuthorize("hasRole('ADMIN') and @adminAccess.hasAnyRole(authentication, 'FINANCE', 'OPS', 'SUPER_ADMIN')")
    @Operation(summary = "Audit and reconcile internal payments against Cashfree gateway status")
    public ResponseEntity<ApiResponse<com.foodie.payment.service.PaymentReconciliationService.ReconciliationReportDto>> runReconciliation() {
        return ResponseEntity.ok(ApiResponse.success(reconciliationService.runReconciliation()));
    }

    @PostMapping("/weekly-settlement/trigger")
    @PreAuthorize("hasRole('ADMIN') and @adminAccess.hasAnyRole(authentication, 'FINANCE', 'SUPER_ADMIN')")
    @Operation(summary = "Trigger weekly automated payment settlement and payout batch cycle")
    public ResponseEntity<ApiResponse<String>> triggerWeeklySettlement() {
        weeklySettlementScheduler.executeWeeklySettlementCycle();
        return ResponseEntity.ok(ApiResponse.success("Weekly settlement batch processing triggered successfully."));
    }
}

