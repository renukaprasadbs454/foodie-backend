package com.foodie.restaurant.controller;

import com.foodie.common.dto.ApiResponse;
import com.foodie.common.exception.ResourceNotFoundException;
import com.foodie.restaurant.entity.Restaurant;
import com.foodie.restaurant.repository.RestaurantRepository;
import com.foodie.security.principal.AuthPrincipal;
import com.foodie.support.entity.SupportConversation;
import com.foodie.support.entity.SupportMessage;
import com.foodie.support.service.SupportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/restaurants/me/support-tickets")
@Tag(name = "Restaurant Support Tickets")
@CrossOrigin(origins = "*")
public class RestaurantSupportTicketController {

    private final RestaurantRepository restaurantRepository;
    private final SupportService supportService;

    public RestaurantSupportTicketController(RestaurantRepository restaurantRepository, SupportService supportService) {
        this.restaurantRepository = restaurantRepository;
        this.supportService = supportService;
    }

    private Restaurant requireOwnedRestaurant(UUID ownerCredentialId) {
        return restaurantRepository.findByOwnerUserCredentialId(ownerCredentialId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant profile not found for authenticated user."));
    }

    @GetMapping
    @PreAuthorize("hasRole('RESTAURANT')")
    @Operation(summary = "Get all support tickets for authenticated restaurant")
    public ResponseEntity<ApiResponse<List<SupportConversation>>> getMyTickets(
            @AuthenticationPrincipal AuthPrincipal principal) {
        Restaurant restaurant = requireOwnedRestaurant(principal.userId());
        return ResponseEntity.ok(ApiResponse.success(supportService.getRestaurantConversations(restaurant.getId())));
    }

    @PostMapping
    @PreAuthorize("hasRole('RESTAURANT')")
    @Operation(summary = "Create a new support ticket for authenticated restaurant")
    public ResponseEntity<ApiResponse<SupportConversation>> createTicket(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestBody Map<String, String> request) {
        Restaurant restaurant = requireOwnedRestaurant(principal.userId());
        String subject = request.getOrDefault("subject", "Restaurant Support Enquiry");
        String message = request.getOrDefault("message", "");
        String orderId = request.get("orderId");

        SupportConversation conv = supportService.createRestaurantConversation(
                restaurant.getId(),
                restaurant.getName(),
                subject,
                orderId,
                message
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(conv));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('RESTAURANT')")
    @Operation(summary = "Get specific support ticket details with restaurant ownership check")
    public ResponseEntity<ApiResponse<SupportConversation>> getTicketById(
            @PathVariable("id") String ticketId,
            @AuthenticationPrincipal AuthPrincipal principal) {
        Restaurant restaurant = requireOwnedRestaurant(principal.userId());
        return supportService.getRestaurantConversation(ticketId, restaurant.getId())
                .map(conv -> ResponseEntity.ok(ApiResponse.success(conv)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/messages")
    @PreAuthorize("hasRole('RESTAURANT')")
    @Operation(summary = "Get messages for support ticket with restaurant ownership check")
    public ResponseEntity<ApiResponse<List<SupportMessage>>> getTicketMessages(
            @PathVariable("id") String ticketId,
            @AuthenticationPrincipal AuthPrincipal principal) {
        Restaurant restaurant = requireOwnedRestaurant(principal.userId());
        return ResponseEntity.ok(ApiResponse.success(supportService.getRestaurantMessages(ticketId, restaurant.getId())));
    }

    @PostMapping("/{id}/messages")
    @PreAuthorize("hasRole('RESTAURANT')")
    @Operation(summary = "Send a new message from authenticated restaurant")
    public ResponseEntity<ApiResponse<SupportMessage>> sendMessage(
            @PathVariable("id") String ticketId,
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestBody Map<String, String> request) {
        Restaurant restaurant = requireOwnedRestaurant(principal.userId());
        String message = request.getOrDefault("message", "");
        if (message == null || message.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        SupportMessage msg = supportService.addRestaurantMessage(
                ticketId,
                restaurant.getId(),
                restaurant.getName(),
                message.trim()
        );
        return ResponseEntity.ok(ApiResponse.success(msg));
    }
}
