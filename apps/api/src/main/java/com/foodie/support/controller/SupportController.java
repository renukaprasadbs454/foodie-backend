package com.foodie.support.controller;

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
@RequestMapping("/api/v1/support")
@Tag(name = "Customer — Support")
@CrossOrigin(origins = "*")
public class SupportController {

    @Autowired
    private SupportService supportService;

    @Autowired
    private com.foodie.restaurant.repository.RestaurantRepository restaurantRepository;

    private com.foodie.restaurant.entity.Restaurant getAuthenticatedRestaurant(com.foodie.security.principal.AuthPrincipal principal) {
        if (principal != null && principal.userType() == com.foodie.common.enums.UserType.RESTAURANT) {
            return restaurantRepository.findByOwnerUserCredentialId(principal.userId()).orElse(null);
        }
        return null;
    }

    private String extractCustomerId(com.foodie.security.principal.AuthPrincipal principal) {
        if (principal != null) {
            return principal.userId().toString();
        }
        return "CUST-MOCK-123";
    }

    private String extractCustomerId() {
        return "CUST-MOCK-123";
    }

    @GetMapping("/conversations")
    @Operation(summary = "Get active conversation for customer or authenticated restaurant")
    public ResponseEntity<ApiResponse<List<SupportConversation>>> getMyConversations(
            @org.springframework.security.core.annotation.AuthenticationPrincipal com.foodie.security.principal.AuthPrincipal principal) {
        com.foodie.restaurant.entity.Restaurant rest = getAuthenticatedRestaurant(principal);
        if (rest != null) {
            return ResponseEntity.ok(ApiResponse.success(supportService.getRestaurantConversations(rest.getId())));
        }
        return ResponseEntity.ok(ApiResponse.success(supportService.getCustomerConversations(extractCustomerId(principal))));
    }

    @PostMapping("/conversations")
    @Operation(summary = "Create or get active conversation")
    public ResponseEntity<ApiResponse<SupportConversation>> createOrGetConversation(
            @org.springframework.security.core.annotation.AuthenticationPrincipal com.foodie.security.principal.AuthPrincipal principal,
            @RequestBody Map<String, String> req) {
        com.foodie.restaurant.entity.Restaurant rest = getAuthenticatedRestaurant(principal);
        String category = req.getOrDefault("category", rest != null ? "RESTAURANT" : "CUSTOMER");
        String subject = req.getOrDefault("subject", "Live Agent Request");
        String orderId = req.get("orderId");

        if (rest != null) {
            return ResponseEntity.ok(ApiResponse.success(
                    supportService.createRestaurantConversation(rest.getId(), rest.getName(), subject, orderId, null)));
        }

        return ResponseEntity.ok(ApiResponse.success(
                supportService.getOrCreateConversation(extractCustomerId(principal), category, subject, orderId)));
    }

    @PostMapping("/conversations/{id}/escalate")
    @Operation(summary = "Escalate to human agent")
    public ResponseEntity<ApiResponse<SupportConversation>> escalate(@PathVariable("id") String id) {
        return ResponseEntity.ok(ApiResponse.success(supportService.escalateToAgent(id)));
    }

    @GetMapping("/conversations/{id}/messages")
    @Operation(summary = "Get messages for a conversation")
    public ResponseEntity<ApiResponse<List<SupportMessage>>> getMessages(
            @PathVariable("id") String id,
            @org.springframework.security.core.annotation.AuthenticationPrincipal com.foodie.security.principal.AuthPrincipal principal) {
        com.foodie.restaurant.entity.Restaurant rest = getAuthenticatedRestaurant(principal);
        if (rest != null) {
            return ResponseEntity.ok(ApiResponse.success(supportService.getRestaurantMessages(id, rest.getId())));
        }
        return ResponseEntity.ok(ApiResponse.success(supportService.getMessages(id)));
    }

    @PostMapping("/conversations/{id}/messages")
    @Operation(summary = "Send a new message to the conversation")
    public ResponseEntity<ApiResponse<SupportMessage>> sendMessage(
            @PathVariable("id") String ticketId,
            @org.springframework.security.core.annotation.AuthenticationPrincipal com.foodie.security.principal.AuthPrincipal principal,
            @RequestBody Map<String, String> request) {

        String message = request.getOrDefault("message", "");
        com.foodie.restaurant.entity.Restaurant rest = getAuthenticatedRestaurant(principal);
        if (rest != null) {
            SupportMessage msg = supportService.addRestaurantMessage(ticketId, rest.getId(), rest.getName(), message);
            return ResponseEntity.ok(ApiResponse.success(msg));
        }

        String senderName = request.getOrDefault("senderName", "Customer");
        String senderType = request.getOrDefault("senderType", "CUSTOMER");

        SupportMessage msg = supportService.addMessage(ticketId, senderType, extractCustomerId(principal), senderName, message);
        return ResponseEntity.ok(ApiResponse.success(msg));
    }

    // Batch endpoint for legacy sync support if needed
    @PostMapping("/conversations/sync")
    @SuppressWarnings("unchecked")
    public ResponseEntity<ApiResponse<SupportConversation>> syncConversation(@RequestBody Map<String, Object> req) {
        String category = (String) req.getOrDefault("category", "CUSTOMER");
        String subject = (String) req.getOrDefault("subject", "Support Chat");
        String orderId = (String) req.get("orderId");
        String senderName = (String) req.getOrDefault("senderName", "Customer");

        SupportConversation conv = supportService.getOrCreateConversation(extractCustomerId(), category, subject,
                orderId);

        if (req.containsKey("messages")) {
            List<Map<String, String>> messages = (List<Map<String, String>>) req.get("messages");
            for (Map<String, String> msgData : messages) {
                String content = msgData.get("message");
                if (content != null && !content.isEmpty()) {
                    String senderVal = msgData.get("sender");
                    String sender = "AGENT";
                    if ("customer".equalsIgnoreCase(senderVal))
                        sender = "CUSTOMER";
                    else if ("restaurant".equalsIgnoreCase(senderVal))
                        sender = "RESTAURANT";

                    // Only add if not exist? Just push through
                    supportService.addMessage(conv.getId(), sender, extractCustomerId(), senderName, content);
                }
            }
        }

        if ("connect_agent".equals(req.get("action"))) {
            supportService.escalateToAgent(conv.getId());
        } else if ("RESOLVED".equals(req.get("status"))) {
            supportService.resolveConversation(conv.getId());
        }

        return ResponseEntity.ok(ApiResponse.success(conv));
    }

    @PutMapping("/conversations/{id}/resolve")
    @Operation(summary = "Mark conversation as resolved")
    public ResponseEntity<ApiResponse<SupportConversation>> resolve(@PathVariable("id") String id) {
        return ResponseEntity.ok(ApiResponse.success(supportService.resolveConversation(id)));
    }
}
