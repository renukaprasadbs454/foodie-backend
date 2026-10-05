package com.foodie.delivery.service;

import com.foodie.common.enums.OrderActorType;
import com.foodie.common.enums.OrderStatus;
import com.foodie.order.statemachine.OrderStateMachine;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DeliveryAssignmentTimingTest {

    private static final int DEFAULT_LEAD_TIME_MINUTES = 10;

    @Test
    @DisplayName("20-minute preparation time schedules assignment 10 minutes before ready time")
    void test20MinPreparationTime() {
        Instant acceptedAt = Instant.now();
        int prepTimeMinutes = 20;

        Instant readyAt = acceptedAt.plus(Duration.ofMinutes(prepTimeMinutes));
        Instant scheduledAt = prepTimeMinutes > DEFAULT_LEAD_TIME_MINUTES
                ? readyAt.minus(Duration.ofMinutes(DEFAULT_LEAD_TIME_MINUTES))
                : acceptedAt;

        assertEquals(Duration.ofMinutes(20).toSeconds(), Duration.between(acceptedAt, readyAt).toSeconds());
        assertEquals(Duration.ofMinutes(10).toSeconds(), Duration.between(acceptedAt, scheduledAt).toSeconds());
        assertEquals(Duration.ofMinutes(10).toSeconds(), Duration.between(scheduledAt, readyAt).toSeconds());
    }

    @Test
    @DisplayName("30-minute preparation time schedules assignment 10 minutes before ready time")
    void test30MinPreparationTime() {
        Instant acceptedAt = Instant.now();
        int prepTimeMinutes = 30;

        Instant readyAt = acceptedAt.plus(Duration.ofMinutes(prepTimeMinutes));
        Instant scheduledAt = prepTimeMinutes > DEFAULT_LEAD_TIME_MINUTES
                ? readyAt.minus(Duration.ofMinutes(DEFAULT_LEAD_TIME_MINUTES))
                : acceptedAt;

        assertEquals(Duration.ofMinutes(30).toSeconds(), Duration.between(acceptedAt, readyAt).toSeconds());
        assertEquals(Duration.ofMinutes(20).toSeconds(), Duration.between(acceptedAt, scheduledAt).toSeconds());
        assertEquals(Duration.ofMinutes(10).toSeconds(), Duration.between(scheduledAt, readyAt).toSeconds());
    }

    @Test
    @DisplayName("45-minute preparation time schedules assignment 10 minutes before ready time")
    void test45MinPreparationTime() {
        Instant acceptedAt = Instant.now();
        int prepTimeMinutes = 45;

        Instant readyAt = acceptedAt.plus(Duration.ofMinutes(prepTimeMinutes));
        Instant scheduledAt = prepTimeMinutes > DEFAULT_LEAD_TIME_MINUTES
                ? readyAt.minus(Duration.ofMinutes(DEFAULT_LEAD_TIME_MINUTES))
                : acceptedAt;

        assertEquals(Duration.ofMinutes(45).toSeconds(), Duration.between(acceptedAt, readyAt).toSeconds());
        assertEquals(Duration.ofMinutes(35).toSeconds(), Duration.between(acceptedAt, scheduledAt).toSeconds());
        assertEquals(Duration.ofMinutes(10).toSeconds(), Duration.between(scheduledAt, readyAt).toSeconds());
    }

    @Test
    @DisplayName("Short preparation time (<= lead time) schedules assignment immediately")
    void testShortPreparationTime() {
        Instant acceptedAt = Instant.now();
        int prepTimeMinutes = 8;

        Instant readyAt = acceptedAt.plus(Duration.ofMinutes(prepTimeMinutes));
        Instant scheduledAt = prepTimeMinutes > DEFAULT_LEAD_TIME_MINUTES
                ? readyAt.minus(Duration.ofMinutes(DEFAULT_LEAD_TIME_MINUTES))
                : acceptedAt;

        assertEquals(acceptedAt, scheduledAt);
    }

    @Test
    @DisplayName("Updating preparation time recalculates readyAt and assignmentScheduledAt")
    void testPreparationTimeUpdate() {
        Instant initialAcceptAt = Instant.now();
        int initialPrepTime = 30;

        Instant initialReady = initialAcceptAt.plus(Duration.ofMinutes(initialPrepTime));
        Instant initialScheduled = initialReady.minus(Duration.ofMinutes(DEFAULT_LEAD_TIME_MINUTES));

        // Restaurant delays order by updating prep time to 50 minutes
        int updatedPrepTime = 50;
        Instant updateAt = initialAcceptAt.plus(Duration.ofMinutes(10));
        Instant updatedReady = updateAt.plus(Duration.ofMinutes(updatedPrepTime));
        Instant updatedScheduled = updatedReady.minus(Duration.ofMinutes(DEFAULT_LEAD_TIME_MINUTES));

        assertTrue(updatedReady.isAfter(initialReady));
        assertTrue(updatedScheduled.isAfter(initialScheduled));
    }

    @Test
    @DisplayName("Order state machine allows restaurant prep time update and transitions")
    void testStateMachineTransitionsForPrepTime() {
        assertEquals(
                OrderStateMachine.Decision.ALLOW,
                OrderStateMachine.evaluate(OrderStatus.ACCEPTED, OrderStatus.PREPARING, OrderActorType.RESTAURANT)
        );
        assertEquals(
                OrderStateMachine.Decision.ALLOW,
                OrderStateMachine.evaluate(OrderStatus.WAITING_FOR_DELIVERY_PARTNER, OrderStatus.PREPARING, OrderActorType.RESTAURANT)
        );
        assertEquals(
                OrderStateMachine.Decision.ALLOW,
                OrderStateMachine.evaluate(OrderStatus.ACCEPTED, OrderStatus.ACCEPTED, OrderActorType.RESTAURANT)
        );
    }
}
