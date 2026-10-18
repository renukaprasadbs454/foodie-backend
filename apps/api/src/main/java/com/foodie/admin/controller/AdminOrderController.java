package com.foodie.admin.controller;

import com.foodie.admin.dto.request.OverrideOrderStatusRequestDto;
import com.foodie.admin.service.AdminOperationsService;
import com.foodie.auth.repository.UserCredentialRepository;
import com.foodie.common.dto.ApiResponse;
import com.foodie.order.dto.response.OrderResponseDto;
import com.foodie.order.entity.Order;
import com.foodie.common.enums.OrderStatus;
import com.foodie.payment.repository.PaymentRepository;
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
import com.foodie.admin.entity.LocationZone;
import com.foodie.admin.service.AdminZoneService;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/v1/admin/orders")
@Tag(name = "Admin — Order")
public class AdminOrderController {

    private final AdminOperationsService adminOperationsService;
    private final OrderRepository orderRepository;
    private final RestaurantRepository restaurantRepository;
    private final CustomerRepository customerRepository;
    private final UserCredentialRepository userCredentialRepository;
    private final AdminZoneService adminZoneService;

    public AdminOrderController(
            AdminOperationsService adminOperationsService,
            OrderRepository orderRepository,
            RestaurantRepository restaurantRepository,
            CustomerRepository customerRepository,
            UserCredentialRepository userCredentialRepository,
            AdminZoneService adminZoneService) {
        this.adminOperationsService = adminOperationsService;
        this.orderRepository = orderRepository;
        this.restaurantRepository = restaurantRepository;
        this.customerRepository = customerRepository;
        this.userCredentialRepository = userCredentialRepository;
        this.adminZoneService = adminZoneService;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.foodie.admin.security.AdminAccess adminAccess;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private PaymentRepository paymentRepository;

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
            String createdAt,
            String zoneId,
            String zoneName,
            String restaurantId) {

        public AdminOrderSummaryDto(
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
                String createdAt) {
            this(id, orderCode, customerName, customerPhone, storeName, module,
                    itemsSummary, totalAmount, paymentMethod, status, createdAt, null, null, null);
        }
    }

    @GetMapping
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN') and @adminAccess.hasAnyRole(authentication, 'OPS', 'FINANCE', 'SUPER_ADMIN')")
    @Operation(summary = "List all orders from database for admin operations, scoped to active zone if requested")
    public ResponseEntity<ApiResponse<List<AdminOrderSummaryDto>>> listOrders(
            org.springframework.security.core.Authentication authentication,
            @RequestHeader(value = "X-Active-Zone-Id", required = false) String activeZoneHeader,
            @RequestParam(value = "zoneId", required = false) String zoneIdParam) {

        String requestedZoneId = (activeZoneHeader != null && !activeZoneHeader.isBlank())
                ? activeZoneHeader.trim()
                : (zoneIdParam != null && !zoneIdParam.isBlank() ? zoneIdParam.trim() : null);

        String activeZoneId = adminAccess != null
                ? adminAccess.resolveAuthorizedZone(authentication, requestedZoneId)
                : requestedZoneId;

        LocationZone zone = null;
        if (activeZoneId != null && !activeZoneId.equalsIgnoreCase("ALL")) {
            zone = adminZoneService.validateZone(activeZoneId);
        }

        List<Order> orders = orderRepository.findAll();
        List<AdminOrderSummaryDto> result = new ArrayList<>();

        for (Order order : orders) {
            String restaurantName = "Unknown Store";
            String module = "General Dining";
            Restaurant matchedRestaurant = null;
            if (order.getRestaurantId() != null) {
                Restaurant r = restaurantRepository.findById(order.getRestaurantId()).orElse(null);
                if (r != null) {
                    matchedRestaurant = r;
                    restaurantName = r.getName();
                    if (r.getCuisineTypes() != null && r.getCuisineTypes().length > 0) {
                        module = String.join(", ", r.getCuisineTypes());
                    }
                }
            }

            String restZoneId = "dz-bangalore";
            String restZoneName = "Bangalore Zone";
            String restIdStr = order.getRestaurantId() != null ? order.getRestaurantId().toString() : null;

            if (matchedRestaurant != null) {
                String restCity = matchedRestaurant.getAddress() != null ? matchedRestaurant.getAddress().getCity() : null;
                Double restLat = matchedRestaurant.getLatitude() != null ? matchedRestaurant.getLatitude().doubleValue() : null;
                Double restLng = matchedRestaurant.getLongitude() != null ? matchedRestaurant.getLongitude().doubleValue() : null;
                if ((restCity != null && (restCity.toLowerCase().contains("tumkur") || restCity.toLowerCase().contains("tumakuru") || restCity.toLowerCase().contains("tmk")))
                        || (restLat != null && restLat > 13.2 && restLat < 13.5)) {
                    restZoneId = "dz-tumkur";
                    restZoneName = "Tumkur Zone";
                } else {
                    restZoneId = "dz-bangalore";
                    restZoneName = "Bangalore Zone";
                }
            }

            if (zone != null) {
                if (matchedRestaurant == null) {
                    continue;
                }
                Double lat = matchedRestaurant.getLatitude() != null ? matchedRestaurant.getLatitude().doubleValue() : null;
                Double lng = matchedRestaurant.getLongitude() != null ? matchedRestaurant.getLongitude().doubleValue() : null;
                String city = matchedRestaurant.getAddress() != null ? matchedRestaurant.getAddress().getCity() : null;
                if (zone.getId().equalsIgnoreCase(restZoneId)) {
                    // matches restaurant operating zone directly
                } else if (!adminZoneService.isLocationInZone(lat, lng, city, zone)) {
                    continue;
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
                    (order.getStatus() == OrderStatus.DELIVERED || (paymentRepository != null && paymentRepository.findByOrderId(order.getId()).isPresent()))
                            ? (paymentRepository != null ? paymentRepository.findByOrderId(order.getId()).map(p -> p.getStatus() == com.foodie.common.enums.PaymentStatus.CAPTURED ? "ONLINE" : "COD").orElse("COD") : "COD")
                            : "COD",
                    statusStr,
                    order.getCreatedAt() != null ? order.getCreatedAt().toString() : "Just now",
                    restZoneId,
                    restZoneName,
                    restIdStr
            ));
        }

        result.sort((a, b) -> b.createdAt().compareTo(a.createdAt()));

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/{id}/override-status")
    @PreAuthorize("hasRole('ADMIN') and @adminAccess.hasAnyRole(authentication, 'OPS', 'SUPER_ADMIN')")
    @Operation(summary = "Emergency order status override (state-machine graph still enforced)")
    public ResponseEntity<ApiResponse<OrderResponseDto>> overrideStatus(
            org.springframework.security.core.Authentication authentication,
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable("id") UUID orderId,
            @Valid @RequestBody OverrideOrderStatusRequestDto request
    ) {
        if (adminAccess != null && authentication != null) {
            com.foodie.admin.entity.AdminUser admin = adminAccess.requireAdmin(authentication);
            if (admin.getRole().getName() != com.foodie.admin.entity.AdminRoleName.SUPER_ADMIN
                    && admin.getAssignedZoneId() != null && !admin.getAssignedZoneId().isBlank()) {
                Order order = orderRepository.findById(orderId).orElse(null);
                if (order != null && order.getRestaurantId() != null) {
                    Restaurant r = restaurantRepository.findById(order.getRestaurantId()).orElse(null);
                    if (r != null) {
                        LocationZone zone = adminZoneService.validateZone(admin.getAssignedZoneId());
                        Double lat = r.getLatitude() != null ? r.getLatitude().doubleValue() : null;
                        Double lng = r.getLongitude() != null ? r.getLongitude().doubleValue() : null;
                        String city = r.getAddress() != null ? r.getAddress().getCity() : null;
                        if (!adminZoneService.isLocationInZone(lat, lng, city, zone)) {
                            throw new com.foodie.common.exception.ForbiddenException("Cannot modify orders outside your assigned zone.");
                        }
                    }
                }
            }
        }
        return ResponseEntity.ok(ApiResponse.success(
                adminOperationsService.overrideOrderStatus(principal.userId(), orderId, request)));
    }

    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    @org.springframework.transaction.annotation.Transactional
    public void seedInitialOrdersIfEmpty() {
        try {
            if (orderRepository.count() == 0) {
                List<Restaurant> allRestos = restaurantRepository.findAll();
                if (allRestos.isEmpty()) return;

                Restaurant blrResto = allRestos.stream()
                        .filter(r -> r.getAddress() != null && r.getAddress().getCity() != null
                                && (r.getAddress().getCity().toLowerCase().contains("bangalore") || r.getAddress().getCity().toLowerCase().contains("bengaluru")))
                        .findFirst()
                        .orElse(allRestos.get(0));

                Restaurant tmkResto = allRestos.stream()
                        .filter(r -> (r.getAddress() != null && r.getAddress().getCity() != null
                                && (r.getAddress().getCity().toLowerCase().contains("tumkur") || r.getAddress().getCity().toLowerCase().contains("tumakuru")))
                                || (r.getName() != null && r.getName().toLowerCase().contains("tumkur")))
                        .findFirst()
                        .orElse(null);

                List<Customer> customers = customerRepository.findAll();
                Customer blrCust = customers.stream()
                        .filter(c -> c.getFullName() != null && (c.getFullName().toLowerCase().contains("ananya") || c.getFullName().toLowerCase().contains("rahul")))
                        .findFirst()
                        .orElse(!customers.isEmpty() ? customers.get(0) : null);

                Customer tmkCust = customers.stream()
                        .filter(c -> c.getFullName() != null && (c.getFullName().toLowerCase().contains("priya") || c.getFullName().toLowerCase().contains("karthik")))
                        .findFirst()
                        .orElse(null);

                if (blrCust != null && blrResto != null) {
                    Order o1 = Order.place(
                            "ORD-BLR-1001",
                            blrCust.getId(),
                            blrResto.getId(),
                            blrCust.getDefaultAddressId() != null ? blrCust.getDefaultAddressId() : UUID.randomUUID(),
                            BigDecimal.valueOf(450.0),
                            BigDecimal.valueOf(35.0),
                            BigDecimal.ZERO,
                            BigDecimal.valueOf(22.5),
                            BigDecimal.valueOf(507.5),
                            UUID.randomUUID().toString()
                    );
                    o1.transitionTo(OrderStatus.PREPARING);
                    orderRepository.save(o1);
                }

                if (tmkCust != null && tmkResto != null) {
                    Order o2 = Order.place(
                            "ORD-TMK-2001",
                            tmkCust.getId(),
                            tmkResto.getId(),
                            tmkCust.getDefaultAddressId() != null ? tmkCust.getDefaultAddressId() : UUID.randomUUID(),
                            BigDecimal.valueOf(280.0),
                            BigDecimal.valueOf(25.0),
                            BigDecimal.ZERO,
                            BigDecimal.valueOf(14.0),
                            BigDecimal.valueOf(319.0),
                            UUID.randomUUID().toString()
                    );
                    o2.transitionTo(OrderStatus.CONFIRMED);
                    orderRepository.save(o2);
                }
            }
        } catch (Exception ignored) {
        }
    }
}
