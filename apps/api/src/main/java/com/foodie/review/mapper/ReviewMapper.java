package com.foodie.review.mapper;

import com.foodie.review.dto.response.RestaurantReviewItemDto;
import com.foodie.review.dto.response.ReviewResponseDto;
import com.foodie.review.entity.Review;

import java.util.List;

public final class ReviewMapper {

    private ReviewMapper() {
    }

    public static ReviewResponseDto toResponse(Review review) {
        return new ReviewResponseDto(
                review.getId(),
                review.getOrderId(),
                review.getRestaurantId(),
                review.getDeliveryPartnerId(),
                review.getRestaurantRating(),
                review.getDeliveryRating() == null ? null : review.getDeliveryRating().intValue(),
                review.getComment(),
                review.getCreatedAt()
        );
    }

    public static RestaurantReviewItemDto toPublicItem(Review review) {
        return toPublicItem(review, "Verified Customer", null, List.of());
    }

    public static RestaurantReviewItemDto toPublicItem(
            Review review,
            String customerName,
            String orderNumber,
            List<String> orderedItems
    ) {
        String resolvedCustomerName = (customerName != null && !customerName.isBlank())
                ? customerName
                : "Verified Customer";
        String resolvedOrderNumber = orderNumber != null ? orderNumber : "";
        String orderInfo = !resolvedOrderNumber.isBlank() ? "#" + resolvedOrderNumber : "";
        List<String> itemsList = orderedItems != null ? orderedItems : List.of();
        String itemInfo = String.join(", ", itemsList);

        return new RestaurantReviewItemDto(
                review.getId(),
                review.getId(),
                resolvedCustomerName,
                true, // Always true for reviews tied to delivered orders
                resolvedOrderNumber,
                orderInfo,
                itemsList,
                itemInfo,
                review.getRestaurantRating(),
                review.getDeliveryRating() == null ? null : review.getDeliveryRating().intValue(),
                review.getComment(),
                review.getCreatedAt()
        );
    }
}
