package com.foodie.order.service.impl;

import com.foodie.order.repository.OrderRepository;
import com.foodie.shared.contract.OrderPaymentPort;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderPaymentPortImpl implements OrderPaymentPort {

    private final OrderRepository orderRepository;

    public OrderPaymentPortImpl(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PayableOrder> findByOrderId(UUID orderId) {
        return orderRepository.findById(orderId)
                .map(order -> new PayableOrder(
                        order.getId(),
                        order.getCustomerId(),
                        order.getStatus(),
                        order.getTotalAmount()
                ));
    }
}
