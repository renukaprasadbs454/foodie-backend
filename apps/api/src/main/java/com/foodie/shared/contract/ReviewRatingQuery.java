package com.foodie.shared.contract;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Narrow Review read for Restaurant and Menu avg_rating recalculation.
 */
public interface ReviewRatingQuery {

    BigDecimal averageRestaurantRating(UUID restaurantId);

    MenuItemRating getMenuItemRating(UUID menuItemId);

    Map<UUID, MenuItemRating> getMenuItemRatings(Collection<UUID> menuItemIds);

    record MenuItemRating(BigDecimal avgRating, long reviewCount) {
        public static MenuItemRating empty() {
            return new MenuItemRating(BigDecimal.ZERO, 0L);
        }
    }
}

