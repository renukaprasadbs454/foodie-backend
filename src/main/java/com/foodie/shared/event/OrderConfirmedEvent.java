package com.foodie.shared.event;

import java.time.Instant;
import java.util.UUID;

public record OrderConfirmedEvent(
        UUID eventId,
        Instant occurredAt,
        UUID orderId
) implements DomainEvent {

    public static OrderConfirmedEvent of(UUID orderId) {
        return new OrderConfirmedEvent(UUID.randomUUID(), Instant.now(), orderId);
    }
}
