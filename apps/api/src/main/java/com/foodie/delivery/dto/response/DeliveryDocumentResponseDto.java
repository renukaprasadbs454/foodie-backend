package com.foodie.delivery.dto.response;

import java.time.Instant;
import java.util.UUID;

public record DeliveryDocumentResponseDto(
        UUID documentId,
        String docType,
        String verificationStatus,
        String fileKey,
        String fileUrl,
        Instant uploadedAt
) {
    public DeliveryDocumentResponseDto(UUID documentId, String docType, String verificationStatus, String fileKey, Instant uploadedAt) {
        this(documentId, docType, verificationStatus, fileKey, resolveFileUrl(fileKey), uploadedAt);
    }

    private static String resolveFileUrl(String fileKey) {
        if (fileKey == null || fileKey.isBlank()) {
            return null;
        }
        if (fileKey.startsWith("http://") || fileKey.startsWith("https://") || fileKey.startsWith("/")) {
            return fileKey;
        }
        return "/api/v1/storage/" + fileKey;
    }
}
