package com.foodie.delivery.service;

import com.foodie.common.enums.OrderStatus;
import com.foodie.order.entity.Order;
import com.foodie.order.repository.OrderRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.foodie.common.enums.DeliveryAssignmentStatus;
import com.foodie.delivery.entity.DeliveryAssignment;
import com.foodie.delivery.repository.DeliveryAssignmentRepository;

@Component
public class DeliveryAssignmentScheduler {

    private static final Logger log = LoggerFactory.getLogger(DeliveryAssignmentScheduler.class);

    private final OrderRepository orderRepository;
    private final DeliveryService deliveryService;
    private final DeliveryAssignmentRepository deliveryAssignmentRepository;

    public DeliveryAssignmentScheduler(
            OrderRepository orderRepository,
            DeliveryService deliveryService,
            DeliveryAssignmentRepository deliveryAssignmentRepository) {
        this.orderRepository = orderRepository;
        this.deliveryService = deliveryService;
        this.deliveryAssignmentRepository = deliveryAssignmentRepository;
    }

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void processScheduledAssignments() {
        Instant now = Instant.now();

        // 1. Process timed-out OFFERED assignments (> 60s without acceptance)
        Instant offerTimeoutCutoff = now.minusSeconds(60);
        List<DeliveryAssignment> staleOffers = deliveryAssignmentRepository.findByStatusAndAssignedAtBefore(
                DeliveryAssignmentStatus.OFFERED,
                offerTimeoutCutoff
        );
        for (DeliveryAssignment stale : staleOffers) {
            try {
                log.info("Delivery offer for order {} timed out after 60s. Reassigning to next nearby partner...", stale.getOrderId());
                stale.markRejected();
                deliveryAssignmentRepository.save(stale);
                deliveryService.createAssignmentForOrder(stale.getOrderId());
            } catch (Exception ex) {
                log.error("Failed to reassign timed-out offer for order {}: {}", stale.getOrderId(), ex.getMessage());
            }
        }

        // 2. Process pending unassigned orders scheduled for partner assignment
        // Orders in CONFIRMED / PLACED must wait for restaurant acceptance first!
        List<OrderStatus> eligibleStatuses = List.of(
                OrderStatus.ACCEPTED,
                OrderStatus.PREPARING,
                OrderStatus.WAITING_FOR_DELIVERY_PARTNER,
                OrderStatus.READY_FOR_PICKUP
        );

        List<Order> unassignedOrders = orderRepository.findByStatusInAndDeliveryPartnerIdIsNull(eligibleStatuses);

        for (Order order : unassignedOrders) {
            Instant scheduledAt = order.getAssignmentScheduledAt();
            boolean isReadyOrWaiting = order.getStatus() == OrderStatus.WAITING_FOR_DELIVERY_PARTNER
                    || order.getStatus() == OrderStatus.READY_FOR_PICKUP;
            boolean isLeadTimeReached = scheduledAt != null && !scheduledAt.isAfter(now);

            // Only assign delivery partner if food is ready / waiting OR remaining prep time is <= 10 mins
            if (isReadyOrWaiting || isLeadTimeReached) {
                // If an active offer is already pending for this order, do not overwrite it — let the partner respond or wait for 60s timeout
                Optional<DeliveryAssignment> existingOpt = deliveryAssignmentRepository.findByOrderId(order.getId());
                if (existingOpt.isPresent() && existingOpt.get().getStatus() == DeliveryAssignmentStatus.OFFERED) {
                    continue;
                }
                try {
                    log.info("Scheduler processing delivery assignment for order {} (status={}, scheduledAt={})",
                            order.getId(), order.getStatus(), scheduledAt);
                    deliveryService.createAssignmentForOrder(order.getId());
                } catch (Exception ex) {
                    log.error("Failed scheduled assignment attempt for order {}: {}", order.getId(), ex.getMessage());
                }
            }
        }
    }
}
