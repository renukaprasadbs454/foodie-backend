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
import com.foodie.delivery.entity.DeliveryAssignment;
import com.foodie.delivery.entity.DeliveryPartner;
import com.foodie.delivery.repository.DeliveryAssignmentRepository;
import com.foodie.delivery.repository.DeliveryPartnerRepository;
import com.foodie.common.util.HashUtils;
import com.foodie.shared.event.DeliveryOfferCreatedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping({"/api/v1/admin/orders", "/api/admin/orders"})
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
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.foodie.delivery.repository.DeliveryPartnerRepository deliveryPartnerRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.foodie.shared.contract.DeliveryPartnerLookup deliveryPartnerLookup;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.foodie.order.repository.OrderItemRepository orderItemRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.foodie.order.repository.OrderStatusEventRepository orderStatusEventRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.foodie.notification.repository.NotificationLogRepository notificationLogRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private DeliveryAssignmentRepository deliveryAssignmentRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private PasswordEncoder passwordEncoder;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private ApplicationEventPublisher eventPublisher;


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

    @GetMapping("/{id}")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN') and @adminAccess.hasAnyRole(authentication, 'OPS', 'FINANCE', 'SUPER_ADMIN')")
    @Operation(summary = "Get single order details for admin with delivery partner information")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> getOrderById(
            @PathVariable("id") UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new com.foodie.common.exception.ResourceNotFoundException("Order not found."));

        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("id", order.getId().toString());
        data.put("orderId", order.getId().toString());
        data.put("orderNumber", order.getOrderNumber());
        data.put("status", order.getStatus() != null ? order.getStatus().name() : "PENDING");
        data.put("customerId", order.getCustomerId() != null ? order.getCustomerId().toString() : null);
        data.put("restaurantId", order.getRestaurantId() != null ? order.getRestaurantId().toString() : null);
        data.put("addressId", order.getAddressId() != null ? order.getAddressId().toString() : null);
        data.put("subtotal", order.getSubtotal());
        data.put("deliveryFee", order.getDeliveryFee());
        data.put("discountAmount", order.getDiscountAmount());
        data.put("taxAmount", order.getTaxAmount());
        data.put("totalAmount", order.getTotalAmount());
        data.put("placedAt", order.getPlacedAt() != null ? order.getPlacedAt().toString() : null);

        if (order.getCustomerId() != null) {
            customerRepository.findById(order.getCustomerId()).ifPresent(c -> {
                if (c.getFullName() != null) data.put("customerName", c.getFullName());
                if (c.getUserCredentialId() != null) {
                    userCredentialRepository.findById(c.getUserCredentialId()).ifPresent(cred -> {
                        if (cred.getPhoneNumber() != null) data.put("customerPhone", cred.getPhoneNumber());
                    });
                }
            });
        }

        if (order.getRestaurantId() != null) {
            restaurantRepository.findById(order.getRestaurantId()).ifPresent(r -> {
                if (r.getName() != null) data.put("restaurantName", r.getName());
            });
        }

        UUID dpId = order.getDeliveryPartnerId();
        if (dpId != null) {
            data.put("deliveryPartnerId", dpId.toString());
            data.put("assignedDeliveryPartnerId", dpId.toString());
            String dpName = "Delivery Partner";
            String dpPhone = "";

            if (deliveryPartnerRepository != null) {
                var dpOpt = deliveryPartnerRepository.findById(dpId);
                if (dpOpt.isPresent()) {
                    var dp = dpOpt.get();
                    if (dp.getFullName() != null && !dp.getFullName().isBlank()) {
                        dpName = dp.getFullName();
                    }
                    if (dp.getUserCredentialId() != null) {
                        var credOpt = userCredentialRepository.findById(dp.getUserCredentialId());
                        if (credOpt.isPresent() && credOpt.get().getPhoneNumber() != null) {
                            dpPhone = credOpt.get().getPhoneNumber();
                        }
                    }
                }
            } else if (deliveryPartnerLookup != null) {
                var summaryOpt = deliveryPartnerLookup.findPartnerSummaryById(dpId);
                if (summaryOpt.isPresent()) {
                    var s = summaryOpt.get();
                    if (s.fullName() != null && !s.fullName().isBlank()) {
                        dpName = s.fullName();
                    }
                    if (s.phoneNumber() != null && !s.phoneNumber().isBlank()) {
                        dpPhone = s.phoneNumber();
                    } else if (s.userCredentialId() != null) {
                        var credOpt = userCredentialRepository.findById(s.userCredentialId());
                        if (credOpt.isPresent() && credOpt.get().getPhoneNumber() != null) {
                            dpPhone = credOpt.get().getPhoneNumber();
                        }
                    }
                }
            }

            data.put("deliveryPartnerName", dpName);
            data.put("deliveryPartnerPhone", dpPhone);
            data.put("assignedDeliveryPartnerName", dpName);
            data.put("assignedDeliveryPartnerPhone", dpPhone);

            java.util.Map<String, Object> partnerMap = new java.util.HashMap<>();
            partnerMap.put("id", dpId.toString());
            partnerMap.put("fullName", dpName);
            partnerMap.put("name", dpName);
            partnerMap.put("phoneNumber", dpPhone);
            partnerMap.put("phone", dpPhone);
            data.put("assignedDeliveryPartner", partnerMap);
        } else {
            data.put("deliveryPartnerId", null);
            data.put("deliveryPartnerName", null);
            data.put("deliveryPartnerPhone", null);
            data.put("assignedDeliveryPartner", null);
            data.put("assignedDeliveryPartnerId", null);
            data.put("assignedDeliveryPartnerName", null);
            data.put("assignedDeliveryPartnerPhone", null);
        }

        if (orderItemRepository != null) {
            List<com.foodie.order.entity.OrderItem> items = orderItemRepository.findByOrderIdOrderByCreatedAtAsc(order.getId());
            List<java.util.Map<String, Object>> itemDtos = new ArrayList<>();
            for (var it : items) {
                java.util.Map<String, Object> im = new java.util.HashMap<>();
                im.put("menuItemId", it.getMenuItemId() != null ? it.getMenuItemId().toString() : null);
                im.put("name", "Order Item");
                im.put("quantity", it.getQuantity());
                im.put("unitPrice", it.getUnitPrice());
                im.put("lineTotal", it.getLineTotal());
                itemDtos.add(im);
            }
            data.put("items", itemDtos);
        }

        if (orderStatusEventRepository != null) {
            List<com.foodie.order.entity.OrderStatusEvent> events = orderStatusEventRepository.findByOrderIdOrderByCreatedAtAsc(order.getId());
            List<java.util.Map<String, Object>> eventDtos = new ArrayList<>();
            for (var ev : events) {
                java.util.Map<String, Object> em = new java.util.HashMap<>();
                em.put("eventId", ev.getId() != null ? ev.getId().toString() : null);
                em.put("fromStatus", ev.getFromStatus() != null ? ev.getFromStatus().name() : null);
                em.put("toStatus", ev.getToStatus() != null ? ev.getToStatus().name() : null);
                em.put("actorType", ev.getActorType() != null ? ev.getActorType().name() : null);
                em.put("reason", ev.getReason());
                em.put("createdAt", ev.getCreatedAt() != null ? ev.getCreatedAt().toString() : null);
                eventDtos.add(em);
            }
            data.put("orderStatusEvents", eventDtos);
        }

        return ResponseEntity.ok(ApiResponse.success(data));
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

    @PostMapping(value = {"/{id}/assign-delivery-partner", "/{id}/assign"})
    @org.springframework.transaction.annotation.Transactional
    @PreAuthorize("hasRole('ADMIN') and @adminAccess.hasAnyRole(authentication, 'OPS', 'SUPER_ADMIN')")
    @Operation(summary = "Super Admin manual delivery partner assignment to order")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> assignDeliveryPartner(
            @PathVariable("id") UUID orderId,
            @RequestBody java.util.Map<String, Object> body) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new com.foodie.common.exception.ResourceNotFoundException("Order not found."));

        String dpIdStr = (String) body.get("deliveryPartnerId");
        if (dpIdStr == null || dpIdStr.isBlank()) {
            throw new com.foodie.common.exception.BadRequestException(
                    com.foodie.common.exception.ErrorCode.VALIDATION_FAILED,
                    "deliveryPartnerId is required.");
        }

        UUID dpUuid;
        try {
            dpUuid = UUID.fromString(dpIdStr);
        } catch (IllegalArgumentException e) {
            dpUuid = UUID.nameUUIDFromBytes(dpIdStr.getBytes());
        }

        // Find delivery partner
        DeliveryPartner partner = null;
        UUID userCredId = null;
        if (deliveryPartnerRepository != null) {
            var dpOpt = deliveryPartnerRepository.findById(dpUuid);
            if (dpOpt.isPresent()) {
                partner = dpOpt.get();
                userCredId = partner.getUserCredentialId();
            }
        }
        if (partner == null) {
            throw new com.foodie.common.exception.ResourceNotFoundException("Delivery partner not found.");
        }

        order.assignDeliveryPartner(partner.getId());
        if (order.getStatus() == OrderStatus.PLACED || order.getStatus() == OrderStatus.CONFIRMED) {
            order.transitionTo(OrderStatus.WAITING_FOR_DELIVERY_PARTNER);
        }
        orderRepository.save(order);

        // Crucial: Create or re-offer DeliveryAssignment entity with OFFERED status so partner receives the order request!
        DeliveryAssignment assignment = null;
        if (deliveryAssignmentRepository != null) {
            var existingAssignmentOpt = deliveryAssignmentRepository.findByOrderId(order.getId());
            if (existingAssignmentOpt.isPresent()) {
                assignment = existingAssignmentOpt.get();
                // If previously assigned to another partner, notify the old partner of cancellation without penalizing them
                if (assignment.getDeliveryPartner() != null && !assignment.getDeliveryPartner().getId().equals(partner.getId())) {
                    DeliveryPartner oldPartner = assignment.getDeliveryPartner();
                    if (notificationLogRepository != null && oldPartner.getUserCredentialId() != null) {
                        try {
                            notificationLogRepository.save(
                                    com.foodie.notification.entity.NotificationLog.create(
                                            oldPartner.getUserCredentialId(),
                                            null,
                                            "Order Cancelled",
                                            "Order #" + order.getOrderNumber() + " has been cancelled.",
                                            com.foodie.common.enums.NotificationDeliveryStatus.SENT
                                    )
                            );
                        } catch (Exception ex) {
                            // ignore notification save failure
                        }
                    }
                }
                assignment.reofferTo(partner);
                assignment = deliveryAssignmentRepository.save(assignment);
            } else {
                String pickupOtp = HashUtils.sixDigitOtp();
                String deliveryOtp = HashUtils.sixDigitOtp();
                String encodedPickup = passwordEncoder != null ? passwordEncoder.encode(pickupOtp) : pickupOtp;
                String encodedDelivery = passwordEncoder != null ? passwordEncoder.encode(deliveryOtp) : deliveryOtp;
                assignment = DeliveryAssignment.createOffered(order.getId(), partner, encodedPickup, encodedDelivery);
                assignment = deliveryAssignmentRepository.save(assignment);
            }

            String restName = "Restaurant";
            String restLocation = "Restaurant Location";
            if (restaurantRepository != null && order.getRestaurantId() != null) {
                var rOpt = restaurantRepository.findById(order.getRestaurantId());
                if (rOpt.isPresent()) {
                    var r = rOpt.get();
                    if (r.getName() != null && !r.getName().isBlank()) {
                        restName = r.getName();
                    }
                    if (r.getAddress() != null) {
                        var addr = r.getAddress();
                        String loc = (addr.getLine1() != null ? addr.getLine1() : "") +
                                (addr.getCity() != null ? (addr.getLine1() != null ? ", " : "") + addr.getCity() : "");
                        if (!loc.isBlank()) {
                            restLocation = loc;
                        }
                    }
                }
            }

            if (eventPublisher != null) {
                eventPublisher.publishEvent(DeliveryOfferCreatedEvent.of(
                        order.getId(),
                        partner.getId(),
                        assignment.getId(),
                        2.5,
                        restName,
                        restLocation
                ));
            }
        }

        UUID targetCredId = userCredId != null ? userCredId : dpUuid;

        // Create notification document for that specific delivery partner
        String notifTitle = "New Order Offer";
        String notifBody = "Order #" + order.getOrderNumber() + " has been assigned to you. Tap to accept now!";
        if (notificationLogRepository != null) {
            try {
                com.foodie.notification.entity.NotificationLog notif =
                        com.foodie.notification.entity.NotificationLog.create(
                                targetCredId,
                                null,
                                notifTitle,
                                notifBody,
                                com.foodie.common.enums.NotificationDeliveryStatus.SENT
                        );
                notificationLogRepository.save(notif);
            } catch (Exception ex) {
                System.err.println("Warning: failed to save NotificationLog: " + ex.getMessage());
            }
        }

        // Send socket event to partner room so mobile app immediately rings with Swiggy sound and pops the offer banner
        if (messagingTemplate != null) {
            try {
                java.util.Map<String, Object> socketPayload = new java.util.LinkedHashMap<>();
                socketPayload.put("type", "DELIVERY_OFFER_CREATED");
                socketPayload.put("event", "DELIVERY_OFFER_CREATED");
                socketPayload.put("assignmentId", assignment != null ? assignment.getId().toString() : null);
                socketPayload.put("orderId", order.getId().toString());
                socketPayload.put("orderNumber", order.getOrderNumber());
                socketPayload.put("deliveryPartnerId", partner.getId().toString());
                socketPayload.put("title", notifTitle);
                socketPayload.put("body", notifBody);
                socketPayload.put("status", "OFFERED");
                socketPayload.put("timestamp", java.time.Instant.now().toString());

                String partnerRoom = "deliveryPartner_" + partner.getId();
                messagingTemplate.convertAndSend("/topic/" + partnerRoom, socketPayload);
                messagingTemplate.convertAndSend(partnerRoom, socketPayload);

                if (userCredId != null) {
                    messagingTemplate.convertAndSend("/topic/user/" + userCredId + "/notifications", socketPayload);
                    messagingTemplate.convertAndSend("/topic/user/" + userCredId + "/offers", socketPayload);
                }
            } catch (Exception ex) {
                System.err.println("Warning: failed to send WebSocket notification: " + ex.getMessage());
            }
        }

        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("orderId", order.getId().toString());
        result.put("orderNumber", order.getOrderNumber());
        result.put("assignedDeliveryPartnerId", partner.getId().toString());
        result.put("status", order.getStatus().name());
        result.put("assignmentId", assignment != null ? assignment.getId().toString() : null);

        return ResponseEntity.ok(ApiResponse.success(result));
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
