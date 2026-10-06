package com.foodie.review.service.impl;

import com.foodie.review.repository.MenuItemRatingProjection;
import com.foodie.review.repository.ReviewRepository;
import com.foodie.shared.contract.ReviewRatingQuery;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReviewRatingQueryImpl implements ReviewRatingQuery {

    private final ReviewRepository reviewRepository;

    public ReviewRatingQueryImpl(ReviewRepository reviewRepository) {
        this.reviewRepository = reviewRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal averageRestaurantRating(UUID restaurantId) {
        Double avg = reviewRepository.averageRestaurantRating(restaurantId);
        return BigDecimal.valueOf(avg == null ? 0.0 : avg).setScale(1, RoundingMode.HALF_UP);
    }

    @Override
    @Transactional(readOnly = true)
    public MenuItemRating getMenuItemRating(UUID menuItemId) {
        if (menuItemId == null) {
            return MenuItemRating.empty();
        }
        Double avg = reviewRepository.averageMenuItemRating(menuItemId);
        Long count = reviewRepository.countMenuItemReviews(menuItemId);
        BigDecimal rating = (avg == null || count == null || count == 0)
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(avg).setScale(1, RoundingMode.HALF_UP);
        return new MenuItemRating(rating, count == null ? 0L : count);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, MenuItemRating> getMenuItemRatings(Collection<UUID> menuItemIds) {
        if (menuItemIds == null || menuItemIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, MenuItemRating> result = new HashMap<>();
        for (UUID id : menuItemIds) {
            result.put(id, MenuItemRating.empty());
        }
        for (MenuItemRatingProjection proj : reviewRepository.findRatingsByMenuItemIds(menuItemIds)) {
            if (proj.getMenuItemId() != null) {
                Double avg = proj.getAvgRating();
                Long count = proj.getReviewCount();
                BigDecimal rating = (avg == null || count == null || count == 0)
                        ? BigDecimal.ZERO
                        : BigDecimal.valueOf(avg).setScale(1, RoundingMode.HALF_UP);
                result.put(proj.getMenuItemId(), new MenuItemRating(rating, count == null ? 0L : count));
            }
        }
        return result;
    }
}

