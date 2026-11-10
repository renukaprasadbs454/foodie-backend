package com.foodie.admin.controller;

import com.foodie.common.dto.ApiResponse;
import com.foodie.coupon.dto.request.CreatePromotionBannerRequest;
import com.foodie.coupon.dto.request.UpdatePromotionBannerRequest;
import com.foodie.coupon.dto.response.PromotionBannerResponse;
import com.foodie.coupon.service.PromotionBannerService;
import com.foodie.security.principal.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/banners")
@Tag(name = "Admin — Promotion Banner")
public class AdminPromotionBannerController {

    private final PromotionBannerService bannerService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.foodie.infrastructure.storage.ObjectStorageClient objectStorageClient;

    public AdminPromotionBannerController(PromotionBannerService bannerService) {
        this.bannerService = bannerService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') and @adminAccess.hasAnyRole(authentication, 'OPS', 'FINANCE', 'SUPER_ADMIN')")
    @Operation(summary = "Get all promotional banners")
    public ResponseEntity<ApiResponse<List<PromotionBannerResponse>>> getAllBanners() {
        return ResponseEntity.ok(ApiResponse.success(bannerService.getAllBanners()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') and @adminAccess.hasAnyRole(authentication, 'OPS', 'FINANCE', 'SUPER_ADMIN')")
    @Operation(summary = "Get a banner by ID")
    public ResponseEntity<ApiResponse<PromotionBannerResponse>> getBanner(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(ApiResponse.success(bannerService.getBanner(id)));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') and @adminAccess.hasAnyRole(authentication, 'OPS', 'FINANCE', 'SUPER_ADMIN')")
    @Operation(summary = "Create a new banner")
    public ResponseEntity<ApiResponse<PromotionBannerResponse>> create(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody CreatePromotionBannerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(bannerService.createBanner(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') and @adminAccess.hasAnyRole(authentication, 'OPS', 'FINANCE', 'SUPER_ADMIN')")
    @Operation(summary = "Update an existing banner")
    public ResponseEntity<ApiResponse<PromotionBannerResponse>> update(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable("id") UUID id,
            @Valid @RequestBody UpdatePromotionBannerRequest request) {
        return ResponseEntity.ok(ApiResponse.success(bannerService.updateBanner(id, request)));
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN') and @adminAccess.hasAnyRole(authentication, 'OPS', 'FINANCE', 'SUPER_ADMIN')")
    @Operation(summary = "Activate a banner")
    public ResponseEntity<ApiResponse<PromotionBannerResponse>> activate(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable("id") UUID id) {
        return ResponseEntity.ok(ApiResponse.success(bannerService.activateBanner(id)));
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN') and @adminAccess.hasAnyRole(authentication, 'OPS', 'FINANCE', 'SUPER_ADMIN')")
    @Operation(summary = "Deactivate a banner")
    public ResponseEntity<ApiResponse<PromotionBannerResponse>> deactivate(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable("id") UUID id) {
        return ResponseEntity.ok(ApiResponse.success(bannerService.deactivateBanner(id)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') and @adminAccess.hasAnyRole(authentication, 'OPS', 'FINANCE', 'SUPER_ADMIN')")
    @Operation(summary = "Delete a banner (soft delete)")
    public ResponseEntity<ApiResponse<Boolean>> delete(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable("id") UUID id) {
        bannerService.deleteBanner(id);
        return ResponseEntity.ok(ApiResponse.success(true));
    }

    @PostMapping(value = "/upload", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN') and @adminAccess.hasAnyRole(authentication, 'OPS', 'FINANCE', 'SUPER_ADMIN')")
    @Operation(summary = "Upload custom promotional banner image or animation file")
    public ResponseEntity<ApiResponse<String>> uploadBannerFile(
            @AuthenticationPrincipal AuthPrincipal principal,
            @org.springframework.web.bind.annotation.RequestPart("file") org.springframework.web.multipart.MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new com.foodie.common.exception.BadRequestException(
                    com.foodie.common.enums.ErrorCode.VALIDATION_FAILED, "File is required.");
        }
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase() : "banner.png";
        String ext = "png";
        if (filename.endsWith(".gif")) ext = "gif";
        else if (filename.endsWith(".webp")) ext = "webp";
        else if (filename.endsWith(".svg")) ext = "svg";
        else if (filename.endsWith(".apng")) ext = "apng";
        else if (filename.endsWith(".jpg") || filename.endsWith(".jpeg")) ext = "jpg";

        String key = "banners/" + UUID.randomUUID() + "." + ext;
        try {
            if (objectStorageClient != null) {
                objectStorageClient.putObject(key, file.getInputStream(), file.getSize(),
                        file.getContentType() != null ? file.getContentType() : "image/" + ext);
            }
            String url = "/api/v1/storage/" + key;
            return ResponseEntity.ok(ApiResponse.success(url));
        } catch (java.io.IOException e) {
            throw new com.foodie.common.exception.BadRequestException(
                    com.foodie.common.enums.ErrorCode.BAD_REQUEST, "Failed to upload file.");
        }
    }
}
