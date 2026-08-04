package com.foodie.restaurant.service;

import com.foodie.common.dto.PaginationMeta;
import com.foodie.common.enums.RestaurantDocType;
import com.foodie.common.enums.RestaurantImageType;
import com.foodie.restaurant.dto.request.CreateRestaurantRequestDto;
import com.foodie.restaurant.dto.request.UpdateRestaurantRequestDto;
import com.foodie.restaurant.dto.response.RestaurantDetailResponseDto;
import com.foodie.restaurant.dto.response.RestaurantDocumentResponseDto;
import com.foodie.restaurant.dto.response.RestaurantImageUploadResponseDto;
import com.foodie.restaurant.dto.response.RestaurantSummaryResponseDto;
import java.util.List;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

public interface RestaurantService {

    record PageResult<T>(List<T> items, PaginationMeta pagination) {
    }

    PageResult<RestaurantSummaryResponseDto> search(
            String search,
            String cuisineType,
            Double lat,
            Double lng,
            int page,
            int size,
            String sort
    );

    RestaurantDetailResponseDto getById(UUID restaurantId, UUID callerCredentialId, boolean callerIsAdmin);

    RestaurantDetailResponseDto create(UUID ownerCredentialId, CreateRestaurantRequestDto request);

    RestaurantDetailResponseDto updateMyRestaurant(UUID ownerCredentialId, UpdateRestaurantRequestDto request);

    RestaurantDocumentResponseDto uploadDocument(
            UUID ownerCredentialId,
            RestaurantDocType docType,
            MultipartFile file
    );

    RestaurantImageUploadResponseDto uploadImage(
            UUID ownerCredentialId,
            RestaurantImageType imageType,
            MultipartFile file
    );

    /** Invoked by Admin module (Phase3 §2.3 / API §13.1) — not exposed as restaurant HTTP in Module 3. */
    RestaurantDetailResponseDto approve(UUID restaurantId, UUID adminId);

    /** Invoked by Admin module (Phase3 §2.3 / API §13.2). */
    RestaurantDetailResponseDto suspend(UUID restaurantId, UUID adminId, String reason);

    /** Document verification for Admin Ops — sets verified_at. */
    RestaurantDocumentResponseDto verifyDocument(UUID restaurantId, UUID documentId, UUID adminId);
}
