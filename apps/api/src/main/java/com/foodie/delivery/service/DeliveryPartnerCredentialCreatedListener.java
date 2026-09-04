package com.foodie.delivery.service;

import com.foodie.common.enums.UserType;
import com.foodie.common.enums.VehicleType;
import com.foodie.delivery.entity.DeliveryPartner;
import com.foodie.delivery.repository.DeliveryPartnerRepository;
import com.foodie.shared.event.UserCredentialCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Creates the initial delivery partner row with PENDING status when Auth publishes first-time DELIVERY_PARTNER signup.
 */
@Component
public class DeliveryPartnerCredentialCreatedListener {

    private static final Logger log = LoggerFactory.getLogger(DeliveryPartnerCredentialCreatedListener.class);

    private final DeliveryPartnerRepository deliveryPartnerRepository;

    public DeliveryPartnerCredentialCreatedListener(DeliveryPartnerRepository deliveryPartnerRepository) {
        this.deliveryPartnerRepository = deliveryPartnerRepository;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onUserCredentialCreated(UserCredentialCreatedEvent event) {
        if (event.userType() != UserType.DELIVERY_PARTNER) {
            return;
        }
        if (deliveryPartnerRepository.existsByUserCredentialId(event.userCredentialId())) {
            return;
        }
        DeliveryPartner partner = DeliveryPartner.create(
                event.userCredentialId(),
                "Delivery Partner",
                VehicleType.BIKE,
                null);
        deliveryPartnerRepository.save(partner);
        log.info("Created initial DeliveryPartner (PENDING KYC) for userCredentialId={}", event.userCredentialId());
    }
}
