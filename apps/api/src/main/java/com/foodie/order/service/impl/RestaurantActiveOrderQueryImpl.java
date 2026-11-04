package com.foodie.order.service.impl;

import com.foodie.common.enums.OrderStatus;
import com.foodie.order.repository.OrderRepository;
import com.foodie.shared.contract.RestaurantActiveOrderQuery;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RestaurantActiveOrderQueryImpl implements RestaurantActiveOrderQuery {

    public static final List<OrderStatus> ACTIVE_ACCEPTED_STATUSES = List.of(
            OrderStatus.ACCEPTED,
            OrderStatus.PREPARING,
            OrderStatus.WAITING_FOR_DELIVERY_PARTNER,
            OrderStatus.READY_FOR_PICKUP,
            OrderStatus.ASSIGNED,
            OrderStatus.REACHED_RESTAURANT
    );

    private final OrderRepository orderRepository;

    public RestaurantActiveOrderQueryImpl(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Override
    public boolean hasActiveAcceptedOrders(UUID restaurantId) {
        if (restaurantId == null) {
            return false;
        }
        return orderRepository.existsByRestaurantIdAndStatusIn(restaurantId, ACTIVE_ACCEPTED_STATUSES);
    }

    @Override
    public long countActiveAcceptedOrders(UUID restaurantId) {
        if (restaurantId == null) {
            return 0L;
        }
        return orderRepository.countByRestaurantIdAndStatusIn(restaurantId, ACTIVE_ACCEPTED_STATUSES);
    }
}

