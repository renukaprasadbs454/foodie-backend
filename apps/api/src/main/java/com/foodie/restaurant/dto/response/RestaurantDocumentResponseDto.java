package com.foodie.restaurant.dto.response;

import java.time.Instant;
import java.util.UUID;

public record RestaurantDocumentResponseDto(
        UUID documentId,
        String docType,
        Instant verifiedAt,
        String fileUrl,
        String downloadUrl,
        String documentUrl
) {
    public RestaurantDocumentResponseDto(UUID documentId, String docType, Instant verifiedAt) {
        this(documentId, docType, verifiedAt, null, null, null);
    }
}
