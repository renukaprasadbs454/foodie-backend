package com.foodie.admin.controller;

import com.foodie.common.dto.ApiResponse;
import com.foodie.notification.dto.request.SendBroadcastNotificationRequestDto;
import com.foodie.notification.dto.response.NotificationBroadcastResponseDto;
import com.foodie.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/notifications")
@Tag(name = "Admin — Notification")
public class AdminNotificationController {

    private final NotificationService notificationService;

    public AdminNotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostMapping("/broadcast")
    @PreAuthorize("hasRole('ADMIN') and @adminAccess.hasAnyRole(authentication, 'OPS', 'MARKETING', 'SUPPORT', 'SUPER_ADMIN')")
    @Operation(summary = "Send or schedule a broadcast notification")
    public ResponseEntity<ApiResponse<NotificationBroadcastResponseDto>> sendBroadcast(
            @Valid @RequestBody SendBroadcastNotificationRequestDto request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(notificationService.sendBroadcast(request)));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') and @adminAccess.hasAnyRole(authentication, 'OPS', 'MARKETING', 'SUPPORT', 'SUPER_ADMIN')")
    @Operation(summary = "Get admin broadcast notification history")
    public ResponseEntity<ApiResponse<List<NotificationBroadcastResponseDto>>> getHistory(
            @RequestParam(required = false) String audience,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        var result = notificationService.getBroadcastHistory(audience, page, size);
        return ResponseEntity.ok(ApiResponse.success(result.items(), result.pagination()));
    }
}
