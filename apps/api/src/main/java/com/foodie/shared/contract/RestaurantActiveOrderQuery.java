package com.foodie.shared.contract;

import java.util.UUID;

/**
 * Shared contract for checking active orders for a restaurant.
 * An active customer order is defined as an order that is accepted
 * but not yet completed (DELIVERED) or cancelled (or rejected).
 */
public interface RestaurantActiveOrderQuery {

    boolean hasActiveAcceptedOrders(UUID restaurantId);

    long countActiveAcceptedOrders(UUID restaurantId);
}

