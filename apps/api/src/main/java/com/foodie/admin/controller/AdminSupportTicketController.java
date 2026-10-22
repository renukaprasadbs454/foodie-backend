package com.foodie.admin.controller;

import com.foodie.common.dto.ApiResponse;
import com.foodie.support.entity.SupportConversation;
import com.foodie.support.entity.SupportMessage;
import com.foodie.support.service.SupportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/support-tickets")
@Tag(name = "Admin - Support")
@CrossOrigin(origins = "*")
public class AdminSupportTicketController {

    @Autowired
    private SupportService supportService;

    @Autowired
    private com.foodie.support.repository.SupportConversationRepository conversationRepository;

    @Autowired(required = false)
    private com.foodie.admin.service.AdminZoneService adminZoneService;

    @Autowired(required = false)
    private com.foodie.restaurant.repository.RestaurantRepository restaurantRepository;

    @Autowired(required = false)
    private com.foodie.user.repository.AddressRepository addressRepository;

    @Autowired(required = false)
    private com.foodie.delivery.repository.DeliveryPartnerRepository deliveryPartnerRepository;

    @Autowired(required = false)
    private com.foodie.admin.security.AdminAccess adminAccess;

    private boolean isTicketInZone(SupportConversation conv, com.foodie.admin.entity.LocationZone zone) {
        if (zone == null) return true;

        // Auto-resolve zone_id if missing
        if (conv.getZoneId() == null || conv.getZoneId().isBlank()) {
            String resolved = supportService.resolveZoneId(
                    conv.getRestaurantId(),
                    conv.getOrderId(),
                    conv.getCustomerId(),
                    conv.getCategory());
            if (resolved != null) {
                conv.setZoneId(resolved);
                conversationRepository.save(conv);
            }
        }

        if (conv.getZoneId() != null && !conv.getZoneId().isBlank()) {
            return conv.getZoneId().equalsIgnoreCase(zone.getId())
                    || conv.getZoneId().equalsIgnoreCase(zone.getZoneName())
                    || com.foodie.admin.security.AdminAccess.areZonesEquivalent(conv.getZoneId(), zone.getId());
        }

        if (conv.getRestaurantId() != null && restaurantRepository != null) {
            var r = restaurantRepository.findById(conv.getRestaurantId()).orElse(null);
            if (r != null) {
                return adminZoneService != null && adminZoneService.isLocationInZone(
                        r.getLatitude() != null ? r.getLatitude().doubleValue() : null,
                        r.getLongitude() != null ? r.getLongitude().doubleValue() : null,
                        r.getAddress() != null ? r.getAddress().getCity() : null,
                        zone);
            }
        }

        if ("DELIVERY".equalsIgnoreCase(conv.getCategory()) && deliveryPartnerRepository != null && conv.getCustomerId() != null) {
            try {
                java.util.UUID dpUid = java.util.UUID.fromString(conv.getCustomerId());
                var dp = deliveryPartnerRepository.findByUserCredentialId(dpUid)
                        .or(() -> deliveryPartnerRepository.findById(dpUid))
                        .orElse(null);
                if (dp != null && dp.getZoneId() != null) {
                    return dp.getZoneId().equalsIgnoreCase(zone.getId())
                            || com.foodie.admin.security.AdminAccess.areZonesEquivalent(dp.getZoneId(), zone.getId());
                }
            } catch (Exception ignored) {}
        }

        if (conv.getCustomerId() != null && addressRepository != null) {
            try {
                java.util.UUID custId = java.util.UUID.fromString(conv.getCustomerId());
                var addrs = addressRepository.findByCustomerIdOrderByCreatedAtAsc(custId);
                for (var addr : addrs) {
                    Double lat = addr.getLatitude() != null ? addr.getLatitude().doubleValue() : null;
                    Double lng = addr.getLongitude() != null ? addr.getLongitude().doubleValue() : null;
                    if (adminZoneService != null && adminZoneService.isLocationInZone(lat, lng, addr.getCity(), zone)) {
                        return true;
                    }
                }
            } catch (Exception ignored) {}
        }

        return false;
    }

    private void assertTicketInZone(org.springframework.security.core.Authentication authentication, SupportConversation conv, String activeZoneHeader) {
        String effectiveZone = activeZoneHeader;
        if (adminAccess != null && authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof com.foodie.security.principal.AuthPrincipal) {
            com.foodie.admin.entity.AdminUser admin = adminAccess.requireAdmin(authentication);
            if (admin.getRole().getName() != com.foodie.admin.entity.AdminRoleName.SUPER_ADMIN
                    && admin.getAssignedZoneId() != null && !admin.getAssignedZoneId().isBlank()) {
                effectiveZone = admin.getAssignedZoneId();
            }
        }
        if (effectiveZone == null || effectiveZone.isBlank() || effectiveZone.equalsIgnoreCase("ALL")) {
            return;
        }
        if (adminZoneService == null) return;
        com.foodie.admin.entity.LocationZone zone = adminZoneService.validateZone(effectiveZone.trim());
        if (!isTicketInZone(conv, zone)) {
            throw new com.foodie.common.exception.ForbiddenException("Support ticket is outside your assigned operating zone: " + zone.getZoneName());
        }
    }

    @GetMapping
    @Operation(summary = "Get all support tickets")
    public ResponseEntity<ApiResponse<List<SupportConversation>>> getAllTickets(
            org.springframework.security.core.Authentication authentication,
            @RequestHeader(value = "X-Active-Zone-Id", required = false) String activeZoneHeader,
            @RequestParam(value = "zoneId", required = false) String zoneIdParam) {
        String requestedZoneId = (activeZoneHeader != null && !activeZoneHeader.isBlank())
                ? activeZoneHeader.trim()
                : (zoneIdParam != null && !zoneIdParam.isBlank() ? zoneIdParam.trim() : null);

        String activeZoneId = adminAccess != null
                ? adminAccess.resolveAuthorizedZone(authentication, requestedZoneId)
                : requestedZoneId;

        var conversations = supportService.getAllAdminConversations();
        if (activeZoneId != null && !activeZoneId.equalsIgnoreCase("ALL") && adminZoneService != null) {
            com.foodie.admin.entity.LocationZone zone = adminZoneService.validateZone(activeZoneId);
            conversations = conversations.stream().filter(conv -> isTicketInZone(conv, zone)).toList();
        }

        return ResponseEntity.ok(ApiResponse.success(conversations));
    }

    @GetMapping("/messages")
    @Operation(summary = "Get all support messages scoped to zone")
    public ResponseEntity<ApiResponse<List<SupportMessage>>> getAllSupportMessages(
            org.springframework.security.core.Authentication authentication,
            @RequestHeader(value = "X-Active-Zone-Id", required = false) String activeZoneHeader,
            @RequestParam(value = "zoneId", required = false) String zoneIdParam) {
        String requestedZoneId = (activeZoneHeader != null && !activeZoneHeader.isBlank())
                ? activeZoneHeader.trim()
                : (zoneIdParam != null && !zoneIdParam.isBlank() ? zoneIdParam.trim() : null);

        String activeZoneId = adminAccess != null
                ? adminAccess.resolveAuthorizedZone(authentication, requestedZoneId)
                : requestedZoneId;

        List<SupportMessage> messages = supportService.getAllAdminMessages(activeZoneId);
        return ResponseEntity.ok(ApiResponse.success(messages));
    }

    @GetMapping("/counts")
    @Operation(summary = "Get ticket counts scoped to zone")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getTicketCounts(
            org.springframework.security.core.Authentication authentication,
            @RequestHeader(value = "X-Active-Zone-Id", required = false) String activeZoneHeader,
            @RequestParam(value = "zoneId", required = false) String zoneIdParam) {
        var allTicketsRes = getAllTickets(authentication, activeZoneHeader, zoneIdParam).getBody();
        List<SupportConversation> list = allTicketsRes != null && allTicketsRes.data() != null ? allTicketsRes.data() : List.of();

        long total = list.size();
        long open = list.stream().filter(t -> "OPEN".equalsIgnoreCase(t.getStatus()) || "WAITING_FOR_AGENT".equalsIgnoreCase(t.getStatus())).count();
        long inProgress = list.stream().filter(t -> "IN_PROGRESS".equalsIgnoreCase(t.getStatus()) || "AGENT_ACTIVE".equalsIgnoreCase(t.getStatus()) || "ASSIGNED".equalsIgnoreCase(t.getStatus())).count();
        long resolved = list.stream().filter(t -> "RESOLVED".equalsIgnoreCase(t.getStatus()) || "CLOSED".equalsIgnoreCase(t.getStatus())).count();
        long customer = list.stream().filter(t -> "CUSTOMER".equalsIgnoreCase(t.getCategory())).count();
        long restaurant = list.stream().filter(t -> "RESTAURANT".equalsIgnoreCase(t.getCategory())).count();
        long delivery = list.stream().filter(t -> "DELIVERY".equalsIgnoreCase(t.getCategory())).count();

        Map<String, Long> counts = Map.of(
                "total", total,
                "open", open,
                "inProgress", inProgress,
                "resolved", resolved,
                "customer", customer,
                "restaurant", restaurant,
                "delivery", delivery
        );
        return ResponseEntity.ok(ApiResponse.success(counts));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get support ticket by ID")
    public ResponseEntity<ApiResponse<SupportConversation>> getTicketById(
            org.springframework.security.core.Authentication authentication,
            @RequestHeader(value = "X-Active-Zone-Id", required = false) String activeZoneHeader,
            @PathVariable("id") String id) {
        return supportService.getConversation(id)
                .map(conv -> {
                    assertTicketInZone(authentication, conv, activeZoneHeader);
                    return ResponseEntity.ok(ApiResponse.success(conv));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/messages")
    @Operation(summary = "Get messages for a conversation")
    public ResponseEntity<ApiResponse<List<SupportMessage>>> getMessages(
            org.springframework.security.core.Authentication authentication,
            @RequestHeader(value = "X-Active-Zone-Id", required = false) String activeZoneHeader,
            @PathVariable("id") String id) {
        var opt = supportService.getConversation(id);
        opt.ifPresent(conv -> assertTicketInZone(authentication, conv, activeZoneHeader));
        supportService.markMessagesReadByAdmin(id);
        return ResponseEntity.ok(ApiResponse.success(supportService.getMessages(id)));
    }

    @PostMapping("/{id}/reply")
    @Operation(summary = "Reply to customer support ticket")
    public ResponseEntity<ApiResponse<SupportMessage>> replyToTicket(
            org.springframework.security.core.Authentication authentication,
            @RequestHeader(value = "X-Active-Zone-Id", required = false) String activeZoneHeader,
            @PathVariable("id") String ticketId,
            @RequestBody Map<String, String> request) {

        var opt = supportService.getConversation(ticketId);
        opt.ifPresent(conv -> assertTicketInZone(authentication, conv, activeZoneHeader));

        String replyMessage = request.getOrDefault("message", "");
        String senderName = request.getOrDefault("senderName", "Admin Support");

        SupportMessage msg = supportService.addMessage(ticketId, "AGENT", "ADMIN_1", senderName, replyMessage);
        supportService.markMessagesReadByAdmin(ticketId);

        return ResponseEntity.ok(ApiResponse.success(msg));
    }

    @PostMapping
    @Operation(summary = "Sync or create support ticket")
    @SuppressWarnings("unchecked")
    public ResponseEntity<ApiResponse<SupportConversation>> syncTicket(@RequestBody Map<String, Object> req) {
        String id = (String) req.get("id");
        if (id == null) {
            id = "ENQ-" + java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        }

        String action = (String) req.get("action");
        String category = (String) req.getOrDefault("category", "CUSTOMER");
        String subject = (String) req.getOrDefault("subject", "Live Agent Request");
        String orderId = (String) req.get("orderId");
        String senderName = (String) req.getOrDefault("senderName", "Customer User");
        String senderEmail = (String) req.getOrDefault("senderEmail", "customer@foodie.com");
        String reqZoneId = (String) req.get("zoneId");
        
        SupportConversation conv = conversationRepository.findById(id).orElse(null);
        if (conv == null) {
            conv = new SupportConversation();
            conv.setId(id);
            conv.setCustomerId(senderEmail);
            conv.setCategory(category);
            conv.setSubject(subject);
            conv.setOrderId(orderId);
            conv.setStatus("WAITING_FOR_AGENT");
            if (reqZoneId != null && !reqZoneId.isBlank()) {
                conv.setZoneId(reqZoneId.trim());
            } else {
                conv.setZoneId(supportService.resolveZoneId(null, orderId, senderEmail, category));
            }
            conv.setUpdatedAt(java.time.Instant.now());
            conv = conversationRepository.save(conv);
        }

        if ("reply".equals(action)) {
            String message = (String) req.get("message");
            if (message != null && !message.isEmpty()) {
                supportService.addMessage(conv.getId(), "CUSTOMER", senderEmail, senderName, message);
            }
        } else if ("connect_agent".equals(action)) {
            conv.setStatus("WAITING_FOR_AGENT");
            conversationRepository.save(conv);
            
            if (req.containsKey("messages")) {
                List<Map<String, String>> messages = (List<Map<String, String>>) req.get("messages");
                for (Map<String, String> msgData : messages) {
                    String content = msgData.get("message");
                    String msgSenderName = msgData.getOrDefault("senderName", senderName);
                    String sender = msgData.getOrDefault("sender", "customer");
                    String sType = "customer".equalsIgnoreCase(sender) ? "CUSTOMER" : "AGENT";
                    
                    List<SupportMessage> existingMsg = supportService.getMessages(id);
                    boolean exists = existingMsg.stream().anyMatch(m -> m.getContent() != null && m.getContent().equals(content));
                    if (content != null && !content.trim().isEmpty() && !exists) {
                        supportService.addMessage(conv.getId(), sType, senderEmail, msgSenderName, content);
                    }
                }
            }
        }
        
        return ResponseEntity.ok(ApiResponse.success(conv));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Update support ticket status, e.g. Resolve, In Progress, Reopen")
    public ResponseEntity<ApiResponse<SupportConversation>> updateTicketStatus(
            @PathVariable("id") String ticketId,
            @RequestBody Map<String, String> request) {

        String status = request.getOrDefault("status", "RESOLVED");
        return ResponseEntity.ok(ApiResponse.success(supportService.updateConversationStatus(ticketId, status)));
    }
}
