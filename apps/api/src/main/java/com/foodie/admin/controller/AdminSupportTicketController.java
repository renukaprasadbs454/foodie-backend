package com.foodie.admin.controller;

import com.foodie.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.Instant;

@RestController
@RequestMapping("/api/v1/admin/support-tickets")
@Tag(name = "Admin — Support Tickets")
public class AdminSupportTicketController {

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get all support tickets")
    public ResponseEntity<ApiResponse<List<SupportTicketDto>>> getAllTickets() {
        // Return an empty list for now since ticket entity doesn't exist yet
        return ResponseEntity.ok(ApiResponse.success(Collections.emptyList()));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update support ticket status")
    public ResponseEntity<ApiResponse<SupportTicketDto>> updateTicketStatus(
            @PathVariable("id") String ticketId,
            @RequestBody Map<String, String> request) {

        String status = request.getOrDefault("status", "OPEN");

        SupportTicketDto mockUpdated = new SupportTicketDto(
                ticketId,
                "TKT-" + ticketId.substring(0, Math.min(8, ticketId.length())),
                UUID.randomUUID().toString(),
                "Customer",
                "customer@example.com",
                UUID.randomUUID().toString(),
                "Mock Ticket",
                "OTHER",
                status,
                "LOW",
                Instant.now().toString(),
                Instant.now().toString(),
                request.get("agentNotes"));

        return ResponseEntity.ok(ApiResponse.success(mockUpdated));
    }

    public record SupportTicketDto(
            String id,
            String ticketNumber,
            String customerId,
            String customerName,
            String customerEmail,
            String orderId,
            String subject,
            String category,
            String status,
            String priority,
            String createdAt,
            String updatedAt,
            String agentNotes) {
    }
}
