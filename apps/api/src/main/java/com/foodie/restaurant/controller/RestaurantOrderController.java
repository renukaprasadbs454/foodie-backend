package com.foodie.restaurant.controller;

import com.foodie.common.dto.ApiResponse;
import com.foodie.common.enums.OrderStatus;
import com.foodie.common.exception.BadRequestException;
import com.foodie.common.exception.ErrorCode;
import com.foodie.order.dto.request.TransitionOrderStatusRequestDto;
import com.foodie.order.dto.response.OrderResponseDto;
import com.foodie.order.dto.response.OrderSummaryResponseDto;
import com.foodie.order.service.OrderService;
import com.foodie.security.principal.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/restaurants/me/orders")
@Tag(name = "Restaurant Orders")
public class RestaurantOrderController {

    private final OrderService orderService;

    public RestaurantOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    @PreAuthorize("hasRole('RESTAURANT')")
    @Operation(summary = "List restaurant orders for authenticated restaurant")
    public ResponseEntity<ApiResponse<List<OrderSummaryResponseDto>>> listOrders(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {
        var result = orderService.listForRestaurant(
                principal.userId(), parseStatus(status), page, size, sort);
        return ResponseEntity.ok(ApiResponse.success(result.items(), result.pagination()));
    }

    @GetMapping("/{orderId}")
    @PreAuthorize("hasRole('RESTAURANT')")
    @Operation(summary = "Get single restaurant order by ID")
    public ResponseEntity<ApiResponse<OrderResponseDto>> getOrder(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID orderId) {
        return ResponseEntity.ok(ApiResponse.success(
                orderService.getById(orderId, principal.userId(), principal.userType())));
    }

    @PatchMapping("/{orderId}/status")
    @PreAuthorize("hasRole('RESTAURANT')")
    @Operation(summary = "Update order status for restaurant order")
    public ResponseEntity<ApiResponse<OrderResponseDto>> updateStatus(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID orderId,
            @Valid @RequestBody TransitionOrderStatusRequestDto request) {
        return ResponseEntity.ok(ApiResponse.success(orderService.transition(
                orderId,
                request.targetStatus(),
                request.reason(),
                request.preparationTime(),
                principal.userId(),
                principal.userType())));
    }

    @PostMapping("/{orderId}/reject")
    @PreAuthorize("hasRole('RESTAURANT')")
    @Operation(summary = "Reject order for restaurant")
    public ResponseEntity<ApiResponse<OrderResponseDto>> rejectOrder(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID orderId,
            @RequestParam(required = false) String reason) {
        return ResponseEntity.ok(ApiResponse.success(orderService.transition(
                orderId,
                OrderStatus.CANCELLED,
                reason != null && !reason.isBlank() ? reason : "Rejected by restaurant",
                null,
                principal.userId(),
                principal.userType())));
    }

    @GetMapping("/{orderId}/customer")
    @PreAuthorize("hasRole('RESTAURANT')")
    @Operation(summary = "Get customer contact details for restaurant order")
    public ResponseEntity<ApiResponse<java.util.Map<String, String>>> getOrderCustomer(
            @PathVariable UUID orderId) {
        var contact = orderService.getCustomerContact(orderId);
        if (contact == null) {
            contact = new OrderService.CustomerContact("Customer", "", "");
        }
        java.util.Map<String, String> data = new java.util.HashMap<>();
        data.put("name", contact.name() != null ? contact.name() : "Customer");
        data.put("phone", contact.phone() != null ? contact.phone() : "");
        data.put("address", contact.address() != null ? contact.address() : "");
        data.put("customerName", contact.name() != null ? contact.name() : "Customer");
        data.put("customerPhone", contact.phone() != null ? contact.phone() : "");
        data.put("deliveryAddress", contact.address() != null ? contact.address() : "");
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    @GetMapping("/{orderId}/payment")
    @PreAuthorize("hasRole('RESTAURANT')")
    @Operation(summary = "Get live payment details for restaurant order")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> getOrderPayment(
            @PathVariable String orderId) {
        var paymentInfo = orderService.getPaymentInfoForOrder(orderId);
        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("orderId", orderId);
        data.put("paymentMethod", paymentInfo.method());
        data.put("paymentMode", paymentInfo.method());
        data.put("paymentStatus", paymentInfo.status());
        data.put("transactionId", paymentInfo.transactionId());
        data.put("isCOD", paymentInfo.isCOD());
        data.put("isCod", paymentInfo.isCOD());
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    private static OrderStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return OrderStatus.valueOf(status.trim());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException(ErrorCode.VALIDATION_FAILED, "Invalid status filter.");
        }
    }
}
