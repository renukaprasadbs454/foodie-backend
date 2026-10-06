package com.foodie.review.repository;

import java.util.UUID;

public interface MenuItemRatingProjection {
    UUID getMenuItemId();
    Double getAvgRating();
    Long getReviewCount();
}

