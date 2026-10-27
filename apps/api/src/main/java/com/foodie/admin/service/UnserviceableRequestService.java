package com.foodie.admin.service;

import com.foodie.admin.dto.UnserviceableRequestDto;
import com.foodie.admin.entity.UnserviceableRequest;
import com.foodie.admin.repository.UnserviceableRequestRepository;
import com.foodie.delivery.entity.DeliveryPartner;
import com.foodie.delivery.repository.DeliveryPartnerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class UnserviceableRequestService {

    private static final Logger log = LoggerFactory.getLogger(UnserviceableRequestService.class);

    private final UnserviceableRequestRepository repository;
    private final DeliveryPartnerRepository deliveryPartnerRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    @org.springframework.context.annotation.Lazy
    private com.foodie.restaurant.repository.RestaurantRepository restaurantRepository;

    public UnserviceableRequestService(
            UnserviceableRequestRepository repository,
            DeliveryPartnerRepository deliveryPartnerRepository) {
        this.repository = repository;
        this.deliveryPartnerRepository = deliveryPartnerRepository;
    }

    public List<UnserviceableRequestDto> getAllRequests() {
        return repository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public UnserviceableRequestDto getRequest(String id) {
        if (id == null) return null;
        return repository.findById(id).map(this::mapToDto).orElse(null);
    }

    @Transactional
    public void deleteRequest(String id) {
        if (id != null && repository.existsById(id)) {
            repository.deleteById(id);
        }
    }

    @Transactional
    public UnserviceableRequestDto saveRequest(UnserviceableRequestDto dto) {
        if (dto == null) return null;
        if (dto.getId() == null || dto.getId().isBlank()) {
            dto.setId("req-" + UUID.randomUUID().toString().substring(0, 8));
        }
        if (dto.getStatus() == null || dto.getStatus().isBlank()) {
            dto.setStatus("PENDING");
        }
        if (dto.getCreatedAt() == null) {
            dto.setCreatedAt(Instant.now());
        }

        UnserviceableRequest entity = repository.findById(dto.getId()).orElseGet(UnserviceableRequest::new);
        mapToEntity(dto, entity);
        UnserviceableRequest saved = repository.save(entity);
        return mapToDto(saved);
    }

    @Transactional
    public UnserviceableRequestDto updateStatus(String id, String status) {
        if (id == null) return null;
        UnserviceableRequest entity = repository.findById(id).orElse(null);
        if (entity == null) {
            return null;
        }

        String cleanStatus = status != null ? status.trim().toUpperCase() : "PENDING";
        entity.setStatus(cleanStatus);
        entity.setUpdatedAt(Instant.now());
        UnserviceableRequest saved = repository.save(entity);

        // Update corresponding DeliveryPartner in database if applicable
        syncDeliveryPartnerStatus(entity, cleanStatus);
        syncRestaurantStatus(entity, cleanStatus);

        return mapToDto(saved);
    }

    private void syncRestaurantStatus(UnserviceableRequest entity, String cleanStatus) {
        try {
            if (restaurantRepository == null) return;
            com.foodie.restaurant.entity.Restaurant restaurant = null;
            if (entity.getRestaurantId() != null) {
                restaurant = restaurantRepository.findById(entity.getRestaurantId()).orElse(null);
            }
            if (restaurant == null && entity.getContactPhone() != null && !entity.getContactPhone().isBlank()) {
                List<com.foodie.restaurant.entity.Restaurant> all = restaurantRepository.findAll();
                for (com.foodie.restaurant.entity.Restaurant r : all) {
                    if (entity.getRestaurantName() != null && entity.getRestaurantName().equalsIgnoreCase(r.getName())) {
                        restaurant = r;
                        break;
                    }
                }
            }

            if (restaurant != null) {
                if ("APPROVED".equalsIgnoreCase(cleanStatus)) {
                    restaurant.setOutOfZone(false);
                    restaurant.approve();
                    if (entity.getZoneId() != null && !entity.getZoneId().isBlank()) {
                        restaurant.setZoneId(entity.getZoneId().trim());
                    }
                    if (entity.getZoneName() != null && !entity.getZoneName().isBlank()) {
                        restaurant.setZoneName(entity.getZoneName().trim());
                    }
                } else if ("REJECTED".equalsIgnoreCase(cleanStatus)) {
                    restaurant.reject("Out of active zone onboarding application rejected by admin.");
                } else if ("PENDING".equalsIgnoreCase(cleanStatus)) {
                    restaurant.setOutOfZone(true);
                    restaurant.resubmit();
                }
                restaurantRepository.save(restaurant);
                log.info("Synced restaurant {} status to {}", restaurant.getId(), cleanStatus);
            }
        } catch (Exception e) {
            log.error("Failed to sync restaurant status for request {}: {}", entity.getId(), e.getMessage());
        }
    }

    private void syncDeliveryPartnerStatus(UnserviceableRequest entity, String cleanStatus) {
        try {
            DeliveryPartner partner = null;
            if (entity.getDeliveryPartnerId() != null) {
                partner = deliveryPartnerRepository.findById(entity.getDeliveryPartnerId()).orElse(null);
            }
            if (partner == null && entity.getContactPhone() != null && !entity.getContactPhone().isBlank()) {
                // Find via credential phone
                List<DeliveryPartner> all = deliveryPartnerRepository.findAll();
                for (DeliveryPartner dp : all) {
                    if (entity.getId() != null && entity.getId().contains(dp.getId().toString().substring(0, Math.min(8, dp.getId().toString().length())))) {
                        partner = dp;
                        break;
                    }
                }
            }

            if (partner != null) {
                if ("APPROVED".equalsIgnoreCase(cleanStatus)) {
                    partner.setZoneVerified(true);
                    partner.setLocationApprovalStatus("APPROVED");
                    if (entity.getZoneId() != null && !entity.getZoneId().isBlank()) {
                        partner.setZoneId(entity.getZoneId().trim());
                    }
                    if (entity.getZoneName() != null && !entity.getZoneName().isBlank()) {
                        partner.setZoneName(entity.getZoneName().trim());
                    }
                } else if ("REJECTED".equalsIgnoreCase(cleanStatus)) {
                    partner.setZoneVerified(false);
                    partner.setLocationApprovalStatus("REJECTED");
                } else if ("PENDING".equalsIgnoreCase(cleanStatus)) {
                    partner.setZoneVerified(false);
                    partner.setLocationApprovalStatus("PENDING");
                }
                deliveryPartnerRepository.save(partner);
                log.info("Synced delivery partner {} location status to {}", partner.getId(), cleanStatus);
            }
        } catch (Exception e) {
            log.error("Failed to sync delivery partner status for request {}: {}", entity.getId(), e.getMessage());
        }
    }

    @Transactional
    public UnserviceableRequestDto logDeliveryPartnerUnserviceableRequest(
            String partnerId,
            String partnerName,
            String phone,
            String email,
            Double latitude,
            Double longitude,
            String address,
            String zoneId,
            String zoneName,
            String cityName) {
        return logDeliveryPartnerUnserviceableRequest(
                partnerId, partnerName, phone, email, latitude, longitude, address, zoneId, zoneName, cityName, null);
    }

    @Transactional
    public UnserviceableRequestDto logDeliveryPartnerUnserviceableRequest(
            String partnerId,
            String partnerName,
            String phone,
            String email,
            Double latitude,
            Double longitude,
            String address,
            String zoneId,
            String zoneName,
            String cityName,
            List<String> submittedDocuments) {

        String displayName = (partnerName != null && !partnerName.isBlank() && !partnerName.equalsIgnoreCase("Delivery Partner"))
                ? partnerName.trim()
                : "Delivery Partner";
        String displayArea = (address != null && !address.isBlank())
                ? address.trim()
                : (cityName != null ? cityName : "Unassigned Area");

        UUID parsedPartnerId = null;
        if (partnerId != null && !partnerId.isBlank()) {
            try {
                parsedPartnerId = UUID.fromString(partnerId.trim());
            } catch (Exception ignored) {}
        }

        // Look for an existing request for this delivery partner
        Optional<UnserviceableRequest> existing = Optional.empty();
        if (parsedPartnerId != null) {
            existing = repository.findFirstByDeliveryPartnerId(parsedPartnerId);
        }
        if (existing.isEmpty() && phone != null && !phone.isBlank()) {
            existing = repository.findFirstByContactPhoneAndRequestSource(phone, "DELIVERY_PARTNER");
        }
        if (existing.isEmpty() && email != null && !email.isBlank()) {
            existing = repository.findFirstByContactEmailAndRequestSource(email, "DELIVERY_PARTNER");
        }

        UUID finalPartnerId = parsedPartnerId;
        UnserviceableRequest entity = existing.orElseGet(() -> {
            UnserviceableRequest newReq = new UnserviceableRequest();
            String idSuffix = partnerId != null && partnerId.length() >= 8 ? partnerId.substring(0, 8) : UUID.randomUUID().toString().substring(0, 8);
            newReq.setId("req-dp-" + idSuffix);
            newReq.setCreatedAt(Instant.now());
            if (finalPartnerId != null) {
                newReq.setDeliveryPartnerId(finalPartnerId);
            }
            return newReq;
        });

        if (entity.getDeliveryPartnerId() == null && finalPartnerId != null) {
            entity.setDeliveryPartnerId(finalPartnerId);
        }
        entity.setRequestSource("DELIVERY_PARTNER");
        entity.setRestaurantName("[Delivery Partner] " + displayName);
        entity.setRequesterName(displayName);
        entity.setContactPerson(displayName);
        if (phone != null && !phone.isBlank()) {
            entity.setContactPhone(phone);
        }
        if (email != null && !email.isBlank()) {
            entity.setContactEmail(email);
        }
        entity.setAddress(displayArea);
        entity.setRequestedArea(displayArea);
        entity.setCityName(cityName != null && !cityName.isBlank() ? cityName : "Bangalore");
        if (latitude != null) entity.setLatitude(latitude);
        if (longitude != null) entity.setLongitude(longitude);
        if (zoneId != null && !zoneId.isBlank()) entity.setZoneId(zoneId.trim());
        if (zoneName != null && !zoneName.isBlank()) entity.setZoneName(zoneName.trim());
        entity.setStatus("PENDING");
        entity.setUpdatedAt(Instant.now());

        if (submittedDocuments != null && !submittedDocuments.isEmpty()) {
            entity.setSubmittedDocuments(String.join(", ", submittedDocuments));
        }

        UnserviceableRequest saved = repository.save(entity);

        // Update DeliveryPartner entity location approval status
        if (finalPartnerId != null) {
            deliveryPartnerRepository.findById(finalPartnerId).ifPresent(p -> {
                p.setLocationApprovalStatus("PENDING");
                deliveryPartnerRepository.save(p);
            });
        }

        return mapToDto(saved);
    }

    @Transactional
    public UnserviceableRequestDto logRestaurantUnserviceableRequest(
            UUID restaurantId,
            String restaurantName,
            String phone,
            String email,
            Double latitude,
            Double longitude,
            String address,
            String zoneId,
            String zoneName,
            String cityName,
            Double distanceKm,
            List<String> submittedDocuments) {

        String displayName = (restaurantName != null && !restaurantName.isBlank()) ? restaurantName.trim() : "Restaurant Outlet";
        String displayArea = (address != null && !address.isBlank()) ? address.trim() : (cityName != null ? cityName : "Unassigned Area");

        Optional<UnserviceableRequest> existing = Optional.empty();
        if (restaurantId != null) {
            existing = repository.findFirstByRestaurantId(restaurantId);
        }
        if (existing.isEmpty() && phone != null && !phone.isBlank()) {
            existing = repository.findFirstByContactPhoneAndRequestSource(phone, "RESTAURANT");
        }
        if (existing.isEmpty() && email != null && !email.isBlank()) {
            existing = repository.findFirstByContactEmailAndRequestSource(email, "RESTAURANT");
        }

        UnserviceableRequest entity = existing.orElseGet(() -> {
            UnserviceableRequest newReq = new UnserviceableRequest();
            String idSuffix = restaurantId != null ? restaurantId.toString().substring(0, Math.min(8, restaurantId.toString().length())) : UUID.randomUUID().toString().substring(0, 8);
            newReq.setId("req-rest-" + idSuffix);
            newReq.setCreatedAt(Instant.now());
            if (restaurantId != null) {
                newReq.setRestaurantId(restaurantId);
            }
            return newReq;
        });

        if (entity.getRestaurantId() == null && restaurantId != null) {
            entity.setRestaurantId(restaurantId);
        }
        entity.setRequestSource("RESTAURANT");
        entity.setRestaurantName(displayName);
        entity.setRequesterName(displayName);
        entity.setContactPerson(displayName);
        if (phone != null && !phone.isBlank()) entity.setContactPhone(phone);
        if (email != null && !email.isBlank()) entity.setContactEmail(email);
        entity.setAddress(displayArea);
        entity.setRequestedArea(displayArea);
        entity.setCityName(cityName != null && !cityName.isBlank() ? cityName : "Karnataka");
        if (latitude != null) entity.setLatitude(latitude);
        if (longitude != null) entity.setLongitude(longitude);
        if (zoneId != null && !zoneId.isBlank()) entity.setZoneId(zoneId.trim());
        if (zoneName != null && !zoneName.isBlank()) entity.setZoneName(zoneName.trim());
        if (distanceKm != null) entity.setDistanceFromZoneKm(distanceKm);
        entity.setStatus("PENDING");
        entity.setUpdatedAt(Instant.now());

        if (submittedDocuments != null && !submittedDocuments.isEmpty()) {
            entity.setSubmittedDocuments(String.join(", ", submittedDocuments));
        }

        UnserviceableRequest saved = repository.save(entity);
        return mapToDto(saved);
    }

    private UnserviceableRequestDto mapToDto(UnserviceableRequest entity) {
        if (entity == null) return null;
        UnserviceableRequestDto dto = new UnserviceableRequestDto();
        dto.setId(entity.getId());
        if (entity.getDeliveryPartnerId() != null) {
            dto.setDeliveryPartnerId(entity.getDeliveryPartnerId().toString());
        }
        if (entity.getRestaurantId() != null) {
            dto.setRestaurantId(entity.getRestaurantId().toString());
        }
        dto.setRestaurantName(entity.getRestaurantName());
        dto.setContactPerson(entity.getContactPerson());
        dto.setContactEmail(entity.getContactEmail());
        dto.setContactPhone(entity.getContactPhone());
        dto.setAddress(entity.getAddress());
        dto.setCityName(entity.getCityName());
        dto.setLatitude(entity.getLatitude());
        dto.setLongitude(entity.getLongitude());
        dto.setStatus(entity.getStatus());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setRequestSource(entity.getRequestSource());
        dto.setRequesterName(entity.getRequesterName());
        dto.setRequestedArea(entity.getRequestedArea());
        dto.setZoneId(entity.getZoneId());
        dto.setZoneName(entity.getZoneName());
        dto.setDistanceFromZoneKm(entity.getDistanceFromZoneKm());

        if (entity.getSubmittedDocuments() != null && !entity.getSubmittedDocuments().isBlank()) {
            dto.setSubmittedDocuments(Arrays.stream(entity.getSubmittedDocuments().split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList()));
        } else {
            dto.setSubmittedDocuments(new ArrayList<>());
        }
        return dto;
    }

    private void mapToEntity(UnserviceableRequestDto dto, UnserviceableRequest entity) {
        if (dto.getId() != null) entity.setId(dto.getId());
        if (dto.getDeliveryPartnerId() != null) {
            try {
                entity.setDeliveryPartnerId(UUID.fromString(dto.getDeliveryPartnerId()));
            } catch (Exception ignored) {}
        }
        if (dto.getRestaurantId() != null) {
            try {
                entity.setRestaurantId(UUID.fromString(dto.getRestaurantId()));
            } catch (Exception ignored) {}
        }
        entity.setRestaurantName(dto.getRestaurantName());
        entity.setContactPerson(dto.getContactPerson());
        entity.setContactEmail(dto.getContactEmail());
        entity.setContactPhone(dto.getContactPhone());
        entity.setAddress(dto.getAddress());
        entity.setCityName(dto.getCityName());
        entity.setLatitude(dto.getLatitude());
        entity.setLongitude(dto.getLongitude());
        entity.setStatus(dto.getStatus() != null ? dto.getStatus() : "PENDING");
        if (dto.getCreatedAt() != null) entity.setCreatedAt(dto.getCreatedAt());
        entity.setUpdatedAt(Instant.now());
        entity.setRequestSource(dto.getRequestSource() != null ? dto.getRequestSource() : "CUSTOMER");
        entity.setRequesterName(dto.getRequesterName());
        entity.setRequestedArea(dto.getRequestedArea());
        entity.setZoneId(dto.getZoneId());
        entity.setZoneName(dto.getZoneName());
        if (dto.getDistanceFromZoneKm() != null) {
            entity.setDistanceFromZoneKm(dto.getDistanceFromZoneKm());
        }
        if (dto.getSubmittedDocuments() != null && !dto.getSubmittedDocuments().isEmpty()) {
            entity.setSubmittedDocuments(String.join(", ", dto.getSubmittedDocuments()));
        }
    }
}
