package com.foodie.admin.controller;

import com.foodie.admin.dto.request.OverrideOrderStatusRequestDto;
import com.foodie.admin.service.AdminOperationsService;
import com.foodie.auth.repository.UserCredentialRepository;
import com.foodie.common.dto.ApiResponse;
import com.foodie.order.dto.response.OrderResponseDto;
import com.foodie.order.entity.Order;
import com.foodie.order.repository.OrderRepository;
import com.foodie.restaurant.entity.Restaurant;
import com.foodie.restaurant.repository.RestaurantRepository;
import com.foodie.security.principal.AuthPrincipal;
import com.foodie.user.entity.Customer;
import com.foodie.user.repository.CustomerRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/orders")
@Tag(name = "Admin — Order")
public class AdminOrderController {

    private final AdminOperationsService adminOperationsService;
    private final OrderRepository orderRepository;
    private final RestaurantRepository restaurantRepository;
    private final CustomerRepository customerRepository;
    private final UserCredentialRepository userCredentialRepository;

    public AdminOrderController(
            AdminOperationsService adminOperationsService,
            OrderRepository orderRepository,
            RestaurantRepository restaurantRepository,
            CustomerRepository customerRepository,
            UserCredentialRepository userCredentialRepository) {
        this.adminOperationsService = adminOperationsService;
        this.orderRepository = orderRepository;
        this.restaurantRepository = restaurantRepository;
        this.customerRepository = customerRepository;
        this.userCredentialRepository = userCredentialRepository;
    }

    public record AdminOrderSummaryDto(
            String id,
            String orderCode,
            String customerName,
            String customerPhone,
            String storeName,
            String module,
            String itemsSummary,
            BigDecimal totalAmount,
            String paymentMethod,
            String status,
            String createdAt) {}

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') and @adminAccess.hasAnyRole(authentication, 'OPS', 'FINANCE', 'SUPER_ADMIN')")
    @Operation(summary = "List all orders from database for admin operations")
    public ResponseEntity<ApiResponse<List<AdminOrderSummaryDto>>> listOrders() {
        List<Order> orders = orderRepository.findAll();
        List<AdminOrderSummaryDto> result = new ArrayList<>();

        for (Order order : orders) {
            String restaurantName = "Unknown Store";
            String module = "General Dining";
            if (order.getRestaurantId() != null) {
                Restaurant r = restaurantRepository.findById(order.getRestaurantId()).orElse(null);
                if (r != null) {
                    restaurantName = r.getName();
                    if (r.getCuisineTypes() != null && r.getCuisineTypes().length > 0) {
                        module = String.join(", ", r.getCuisineTypes());
                    }
                }
            }

            String customerName = "Customer";
            String[] phoneHolder = new String[]{"+91 98000 00000"};
            if (order.getCustomerId() != null) {
                Customer c = customerRepository.findById(order.getCustomerId()).orElse(null);
                if (c != null) {
                    if (c.getFullName() != null && !c.getFullName().isBlank()) {
                        customerName = c.getFullName();
                    }
                    if (c.getUserCredentialId() != null) {
                        userCredentialRepository.findById(c.getUserCredentialId()).ifPresent(cred -> {
                            if (cred.getPhoneNumber() != null) {
                                phoneHolder[0] = cred.getPhoneNumber();
                            }
                        });
                    }
                }
            }

            String statusStr = order.getStatus() != null ? order.getStatus().name() : "PENDING";
            if ("WAITING_FOR_DELIVERY_PARTNER".equals(statusStr)) {
                statusStr = "READY_FOR_PICKUP";
            } else if ("CANCELLED".equals(statusStr)) {
                statusStr = "CANCELED";
            }

            result.add(new AdminOrderSummaryDto(
                    order.getId().toString(),
                    order.getOrderNumber(),
                    customerName,
                    phoneHolder[0],
                    restaurantName,
                    module,
                    "Multi-dish order",
                    order.getTotalAmount() != null ? order.getTotalAmount() : BigDecimal.ZERO,
                    "DIGITAL",
                    statusStr,
                    order.getCreatedAt() != null ? order.getCreatedAt().toString() : "Just now"
            ));
        }

        result.sort((a, b) -> b.createdAt().compareTo(a.createdAt()));

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/{id}/override-status")
    @PreAuthorize("hasRole('ADMIN') and @adminAccess.hasAnyRole(authentication, 'OPS', 'SUPER_ADMIN')")
    @Operation(summary = "Emergency order status override (state-machine graph still enforced)")
    public ResponseEntity<ApiResponse<OrderResponseDto>> overrideStatus(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable("id") UUID orderId,
            @Valid @RequestBody OverrideOrderStatusRequestDto request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                adminOperationsService.overrideOrderStatus(principal.userId(), orderId, request)));
    }
}
