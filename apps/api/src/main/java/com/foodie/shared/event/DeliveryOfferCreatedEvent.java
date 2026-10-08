package com.foodie.shared.event;

import java.time.Instant;
import java.util.UUID;

public record DeliveryOfferCreatedEvent(
        UUID eventId,
        Instant occurredAt,
        UUID orderId,
        UUID deliveryPartnerId,
        UUID assignmentId,
        Double distanceKm,
        String restaurantName,
        String restaurantLocation
) implements DomainEvent {

    public static DeliveryOfferCreatedEvent of(
            UUID orderId, 
            UUID deliveryPartnerId, 
            UUID assignmentId,
            Double distanceKm,
            String restaurantName,
            String restaurantLocation) {
        return new DeliveryOfferCreatedEvent(
                UUID.randomUUID(), 
                Instant.now(), 
                orderId, 
                deliveryPartnerId, 
                assignmentId,
                distanceKm,
                restaurantName,
                restaurantLocation);
    }
}
