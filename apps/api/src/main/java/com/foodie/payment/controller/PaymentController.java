package com.foodie.payment.controller;

import com.foodie.common.dto.ApiResponse;
import com.foodie.payment.dto.request.RefundPaymentRequestDto;
import com.foodie.payment.dto.response.PaymentInitiationResponseDto;
import com.foodie.payment.dto.response.RefundInitiationResponseDto;
import com.foodie.payment.service.PaymentService;
import com.foodie.security.principal.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Autowired;

import com.foodie.payment.dto.request.VerifyPaymentRequestDto;

@RestController
@RequestMapping("/api/v1/payments")
@Tag(name = "Payment")
public class PaymentController {

        private final PaymentService paymentService;

        @Autowired(required = false)
        private com.foodie.delivery.service.DeliveryService deliveryService;

        @Autowired(required = false)
        private com.foodie.delivery.repository.DeliveryCashDepositRepository deliveryCashDepositRepository;

        public PaymentController(PaymentService paymentService) {
                this.paymentService = paymentService;
        }

        @PostMapping("/orders/{orderId}/initiate")
        @PreAuthorize("hasRole('CUSTOMER')")
        @Operation(summary = "Initiate Cashfree payment for an order (idempotent)")
        public ResponseEntity<ApiResponse<PaymentInitiationResponseDto>> initiate(
                        @AuthenticationPrincipal AuthPrincipal principal,
                        @PathVariable UUID orderId,
                        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                        @RequestParam(defaultValue = "false") boolean useWallet) {
                return ResponseEntity.ok(ApiResponse.success(
                                paymentService.initiate(principal.userId(), orderId, idempotencyKey, useWallet)));
        }

        @PostMapping("/verify")
        @PreAuthorize("hasRole('CUSTOMER')")
        @Operation(summary = "Verify Cashfree payment status")
        public ResponseEntity<ApiResponse<Boolean>> verify(
                        @AuthenticationPrincipal AuthPrincipal principal,
                        @Valid @RequestBody VerifyPaymentRequestDto request) {
                return ResponseEntity.ok(ApiResponse.success(
                                paymentService.verifyPayment(principal.userId(), request)));
        }

        @PostMapping("/{paymentId}/refund")
        @PreAuthorize("hasRole('ADMIN') and @adminAccess.hasAnyRole(authentication, 'OPS', 'FINANCE', 'SUPER_ADMIN')")
        @Operation(summary = "Initiate refund (async; finalized by webhook)")
        public ResponseEntity<ApiResponse<RefundInitiationResponseDto>> refund(
                        @AuthenticationPrincipal AuthPrincipal principal,
                        @PathVariable UUID paymentId,
                        @Valid @RequestBody RefundPaymentRequestDto request) {
                return ResponseEntity.status(HttpStatus.ACCEPTED)
                                .body(ApiResponse.success(paymentService.refund(
                                                paymentId, request, principal.userId(), false)));
        }

        @GetMapping(value = "/cashfree/return", produces = org.springframework.http.MediaType.TEXT_HTML_VALUE)
        @Operation(summary = "Cashfree payment return landing page")
        public ResponseEntity<String> handleCashfreeReturn(
                        @RequestParam(name = "order_id", required = false) String cfOrderId) {
                if (cfOrderId != null && deliveryCashDepositRepository != null && deliveryService != null) {
                        try {
                                deliveryCashDepositRepository.findByReferenceNumber(cfOrderId)
                                                .ifPresent(d -> deliveryService.verifyCashDeposit(d.getId()));
                        } catch (Exception ex) {
                                // proceed
                        }
                }
                String safeOrderId = cfOrderId != null ? cfOrderId : "";
                String html = "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\">"
                                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1.0,maximum-scale=1.0,user-scalable=no\">"
                                + "<title>Payment Complete</title>"
                                + "<style>body{margin:0;padding:24px;background:#F8FAFC;font-family:-apple-system,BlinkMacSystemFont,sans-serif;display:flex;flex-direction:column;align-items:center;justify-content:center;height:100vh;box-sizing:border-box;text-align:center;}"
                                + ".card{background:#FFF;padding:32px 24px;border-radius:24px;box-shadow:0 10px 25px -5px rgba(0,0,0,0.05);max-width:360px;width:100%;box-sizing:border-box;}"
                                + ".icon{font-size:48px;margin-bottom:16px;color:#10B981;}"
                                + "h2{margin:0 0 8px 0;font-size:22px;color:#0F172A;font-weight:800;}"
                                + "p{margin:0 0 20px 0;font-size:14px;color:#64748B;line-height:1.5;}"
                                + ".btn{background:#0F3E22;color:#FFF;padding:14px 28px;border-radius:14px;font-size:15px;font-weight:700;border:none;cursor:pointer;width:100%;box-sizing:border-box;}"
                                + "</style></head><body>"
                                + "<div class=\"card\"><div class=\"icon\">✓</div><h2>Payment Received</h2>"
                                + "<p>Your transaction has been processed and confirmed. You may now return to the app.</p>"
                                + "<button class=\"btn\" onclick=\"notifyApp()\">Return to App</button></div>"
                                + "<script>"
                                + "function notifyApp(){"
                                + "  if(window.ReactNativeWebView){"
                                + "    window.ReactNativeWebView.postMessage(JSON.stringify({type:'success',data:{orderId:'" + safeOrderId + "'}}));"
                                + "  }"
                                + "}"
                                + "setTimeout(notifyApp, 1200);"
                                + "</script></body></html>";
                return ResponseEntity.ok(html);
        }
}
