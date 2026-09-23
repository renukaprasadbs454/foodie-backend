package com.foodie.delivery.event;

import java.time.Instant;
import java.util.UUID;

public record DeliveryPartnerStatusChangedEvent(
        UUID partnerId,
        UUID userCredentialId,
        String fullName,
        boolean isOnline,
        Instant lastSeenAt
) {}
