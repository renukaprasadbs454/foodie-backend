package com.foodie.admin.controller;

import com.foodie.admin.dto.request.CommissionConfigDto;
import com.foodie.admin.dto.response.PaymentSettlementResponseDto;
import com.foodie.admin.dto.response.PaymentSplitBreakdownDto;
import com.foodie.admin.service.AdminPaymentService;
import com.foodie.payment.entity.Payment;
import com.foodie.payment.repository.PaymentRepository;
import com.foodie.wallet.entity.LedgerEntry;
import com.foodie.wallet.entity.Payout;
import com.foodie.wallet.repository.LedgerEntryRepository;
import com.foodie.wallet.repository.PayoutRepository;
import com.foodie.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
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

    public AdminPaymentController(
            AdminPaymentService adminPaymentService,
            com.foodie.restaurant.service.RestaurantSettlementService restaurantSettlementService,
            PayoutRepository payoutRepository,
            PaymentRepository paymentRepository,
            LedgerEntryRepository ledgerEntryRepository,
            com.foodie.payment.service.PaymentReconciliationService reconciliationService,
            com.foodie.payment.scheduler.WeeklySettlementScheduler weeklySettlementScheduler) {
        this.adminPaymentService = adminPaymentService;
        this.restaurantSettlementService = restaurantSettlementService;
        this.payoutRepository = payoutRepository;
        this.paymentRepository = paymentRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.reconciliationService = reconciliationService;
        this.weeklySettlementScheduler = weeklySettlementScheduler;
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

    @GetMapping("/payouts")
    @PreAuthorize("hasRole('ADMIN') and @adminAccess.hasAnyRole(authentication, 'FINANCE', 'OPS', 'SUPER_ADMIN')")
    @Operation(summary = "List all payouts for vendors and delivery partners")
    public ResponseEntity<ApiResponse<List<Payout>>> listPayouts() {
        return ResponseEntity.ok(ApiResponse.success(payoutRepository.findAll()));
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

