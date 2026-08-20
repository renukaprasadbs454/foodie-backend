package com.foodie.order.service.impl;

import com.foodie.menu.repository.MenuItemRepository;
import com.foodie.order.entity.Order;
import com.foodie.order.entity.OrderItem;
import com.foodie.order.repository.OrderItemRepository;
import com.foodie.order.repository.OrderRepository;
import com.foodie.shared.contract.MenuItemPriceProvider;
import com.foodie.shared.contract.OrderReviewQuery;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderReviewQueryImpl implements OrderReviewQuery {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final MenuItemPriceProvider menuItemPriceProvider;
    private final MenuItemRepository menuItemRepository;

    public OrderReviewQueryImpl(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            MenuItemPriceProvider menuItemPriceProvider,
            MenuItemRepository menuItemRepository
    ) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.menuItemPriceProvider = menuItemPriceProvider;
        this.menuItemRepository = menuItemRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OrderReviewSnapshot> findByOrderId(UUID orderId) {
        return orderRepository.findById(orderId).map(order -> new OrderReviewSnapshot(
                order.getId(),
                order.getCustomerId(),
                order.getRestaurantId(),
                order.getDeliveryPartnerId(),
                order.getStatus()
        ));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, OrderDetailsSnapshot> findOrderDetailsByOrderIds(Collection<UUID> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return Map.of();
        }
        List<Order> orders = orderRepository.findAllById(orderIds);
        List<OrderItem> items = orderItemRepository.findByOrderIdIn(orderIds);

        Map<UUID, List<String>> orderItemsMap = new HashMap<>();
        for (OrderItem item : items) {
            String name = menuItemPriceProvider.getPriceSnapshot(item.getMenuItemId(), item.getVariantId())
                    .map(MenuItemPriceProvider.MenuItemPriceSnapshot::itemName)
                    .orElse("Menu Item");
            orderItemsMap.computeIfAbsent(item.getOrder().getId(), k -> new ArrayList<>()).add(name);
        }

        Map<UUID, OrderDetailsSnapshot> result = new HashMap<>();
        for (Order order : orders) {
            List<String> itemNames = orderItemsMap.getOrDefault(order.getId(), List.of());
            result.put(order.getId(), new OrderDetailsSnapshot(
                    order.getId(),
                    order.getOrderNumber(),
                    itemNames
            ));
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> findOrderIdsBySearchText(String search) {
        if (search == null || search.isBlank()) {
            return List.of();
        }
        String query = search.trim();
        Set<UUID> orderIds = new HashSet<>();

        List<Order> matchingOrders = orderRepository.findByOrderNumberContainingIgnoreCase(query);
        for (Order o : matchingOrders) {
            orderIds.add(o.getId());
        }

        var matchingMenuItems = menuItemRepository.findByNameContainingIgnoreCase(query);
        if (!matchingMenuItems.isEmpty()) {
            List<UUID> menuItemIds = matchingMenuItems.stream().map(com.foodie.menu.entity.MenuItem::getId).toList();
            List<OrderItem> matchingOrderItems = orderItemRepository.findByMenuItemIdIn(menuItemIds);
            for (OrderItem item : matchingOrderItems) {
                orderIds.add(item.getOrder().getId());
            }
        }

        return new ArrayList<>(orderIds);
    }
}
