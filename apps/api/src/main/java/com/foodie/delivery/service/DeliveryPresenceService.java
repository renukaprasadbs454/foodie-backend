package com.foodie.delivery.service;

import com.foodie.delivery.entity.DeliveryPartner;
import com.foodie.delivery.event.DeliveryPartnerStatusChangedEvent;
import com.foodie.delivery.repository.DeliveryPartnerRepository;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeliveryPresenceService {

    private static final Logger log = LoggerFactory.getLogger(DeliveryPresenceService.class);
    private static final long STALE_PRESENCE_THRESHOLD_SECONDS = 30;

    private final DeliveryPartnerRepository deliveryPartnerRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ApplicationEventPublisher eventPublisher;

    public DeliveryPresenceService(
            DeliveryPartnerRepository deliveryPartnerRepository,
            SimpMessagingTemplate messagingTemplate,
            ApplicationEventPublisher eventPublisher) {
        this.deliveryPartnerRepository = deliveryPartnerRepository;
        this.messagingTemplate = messagingTemplate;
        this.eventPublisher = eventPublisher;
    }

    public void updatePresence(DeliveryPartner partner, boolean isOnline) {
        partner.setOnline(isOnline);
        partner.touchLastSeen();
        deliveryPartnerRepository.save(partner);
        broadcastStatusChange(partner);
    }

    public void touchPresence(DeliveryPartner partner) {
        partner.touchLastSeen();
        if (!partner.isOnline()) {
            partner.setOnline(true);
        }
        deliveryPartnerRepository.save(partner);
        broadcastStatusChange(partner);
    }

    public void broadcastStatusChange(DeliveryPartner partner) {
        DeliveryPartnerStatusChangedEvent event = new DeliveryPartnerStatusChangedEvent(
                partner.getId(),
                partner.getUserCredentialId(),
                partner.getFullName(),
                partner.isOnline(),
                partner.getLastSeenAt() != null ? partner.getLastSeenAt() : Instant.now()
        );
        eventPublisher.publishEvent(event);
        try {
            messagingTemplate.convertAndSend("/topic/admin/delivery-partners/status", event);
        } catch (Exception ex) {
            log.warn("Failed to broadcast delivery partner status change via STOMP: {}", ex.getMessage());
        }
    }

    @Scheduled(fixedDelay = 10000)
    @Transactional
    public void cleanupStaleOnlineSessions() {
        Instant cutoff = Instant.now().minusSeconds(STALE_PRESENCE_THRESHOLD_SECONDS);
        List<DeliveryPartner> onlinePartners = deliveryPartnerRepository.findAll().stream()
                .filter(DeliveryPartner::isOnline)
                .filter(p -> p.getLastSeenAt() == null || p.getLastSeenAt().isBefore(cutoff))
                .toList();

        if (!onlinePartners.isEmpty()) {
            log.info("Found {} delivery partners with stale presence (>30s). Marking OFFLINE.", onlinePartners.size());
            for (DeliveryPartner partner : onlinePartners) {
                partner.setOnline(false);
                partner.touchLastSeen();
                deliveryPartnerRepository.save(partner);
                broadcastStatusChange(partner);
            }
        }
    }
}
