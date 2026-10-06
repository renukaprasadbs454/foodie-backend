package com.foodie.review.mapper;

import com.foodie.review.dto.response.RestaurantReviewItemDto;
import com.foodie.review.dto.response.ReviewResponseDto;
import com.foodie.review.entity.Review;

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
        return toPublicItem(review, null);
    }

    public static RestaurantReviewItemDto toPublicItem(Review review, String customerName) {
        return new RestaurantReviewItemDto(
                review.getId(),
                review.getOrderId(),
                review.getCustomerId(),
                customerName != null && !customerName.isBlank() ? customerName : "Verified Customer",
                review.getRestaurantRating(),
                review.getDeliveryRating() == null ? null : review.getDeliveryRating().intValue(),
                review.getComment(),
                review.getCreatedAt()
        );
    }
}
