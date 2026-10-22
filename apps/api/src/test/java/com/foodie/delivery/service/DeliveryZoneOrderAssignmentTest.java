package com.foodie.delivery.service;

import com.foodie.admin.entity.LocationZone;
import com.foodie.admin.repository.LocationZoneRepository;
import com.foodie.common.enums.OrderStatus;
import com.foodie.common.enums.VehicleType;
import com.foodie.delivery.config.DeliveryProperties;
import com.foodie.delivery.entity.DeliveryAssignment;
import com.foodie.delivery.entity.DeliveryPartner;
import com.foodie.delivery.repository.DeliveryAssignmentRepository;
import com.foodie.delivery.repository.DeliveryPartnerRepository;
import com.foodie.delivery.service.impl.DeliveryServiceImpl;
import com.foodie.delivery.service.PartnerGeoService;
import com.foodie.shared.contract.OrderDeliveryPort;
import com.foodie.shared.contract.RestaurantPickupQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeliveryZoneOrderAssignmentTest {

    @Mock
    private DeliveryPartnerRepository deliveryPartnerRepository;

    @Mock
    private DeliveryAssignmentRepository deliveryAssignmentRepository;

    @Mock
    private LocationZoneRepository locationZoneRepository;

    @Mock
    private RestaurantPickupQuery restaurantPickupQuery;

    @Mock
    private OrderDeliveryPort orderDeliveryPort;

    @Mock
    private PartnerGeoService partnerGeoService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private DeliveryProperties deliveryProperties;

    @InjectMocks
    private DeliveryServiceImpl deliveryService;

    private LocationZone tumkurZone;
    private LocationZone bangaloreZone;
    private UUID orderId;
    private UUID restaurantId;

    @BeforeEach
    void setUp() {
        orderId = UUID.randomUUID();
        restaurantId = UUID.randomUUID();

        tumkurZone = new LocationZone();
        tumkurZone.setId("dz-tumkur");
        tumkurZone.setZoneName("Tumkur Zone");
        tumkurZone.setCityName("Tumakuru");
        tumkurZone.setStatus("ACTIVE");
        tumkurZone.setDeliveryPartnerEnabled(true);
        tumkurZone.setLatitude(13.34088);
        tumkurZone.setLongitude(77.10066);
        tumkurZone.setRadiusKm(15.0);

        bangaloreZone = new LocationZone();
        bangaloreZone.setId("dz-bangalore");
        bangaloreZone.setZoneName("Bangalore Zone");
        bangaloreZone.setCityName("Bengaluru");
        bangaloreZone.setStatus("ACTIVE");
        bangaloreZone.setDeliveryPartnerEnabled(true);
        bangaloreZone.setLatitude(12.9716);
        bangaloreZone.setLongitude(77.5946);
        bangaloreZone.setRadiusKm(25.0);

        deliveryService.setLocationZoneRepository(locationZoneRepository);

        lenient().when(deliveryProperties.getOfferRadiusKm()).thenReturn(10.0);
        lenient().when(deliveryAssignmentRepository.findByOrderId(orderId)).thenReturn(Optional.empty());
        lenient().when(deliveryAssignmentRepository.findAll()).thenReturn(Collections.emptyList());
        lenient().when(passwordEncoder.encode(any())).thenReturn("encoded-otp");
        lenient().when(deliveryAssignmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OrderDeliveryPort.OrderDeliverySnapshot orderSnapshot = new OrderDeliveryPort.OrderDeliverySnapshot(
                orderId,
                "ORD-12345",
                restaurantId,
                OrderStatus.READY_FOR_PICKUP,
                null,
                UUID.randomUUID(),
                java.time.Instant.now(),
                java.time.Instant.now(),
                BigDecimal.valueOf(500.00)
        );
        lenient().when(orderDeliveryPort.findByOrderId(orderId)).thenReturn(Optional.of(orderSnapshot));
    }

    @Test
    @DisplayName("Only delivery partner with same KYC zone as restaurant is assigned the order")
    void orderAssignedOnlyToPartnerInSameKycZone() {
        // Restaurant is in Tumkur zone
        RestaurantPickupQuery.PickupLocation tumkurPickup = new RestaurantPickupQuery.PickupLocation(
                restaurantId,
                "Tumkur Biryani",
                "MG Road, Tumkur",
                null,
                "Tumkur",
                "572101",
                BigDecimal.valueOf(13.34088),
                BigDecimal.valueOf(77.10066),
                "dz-tumkur",
                "Tumkur Zone"
        );
        when(restaurantPickupQuery.findByRestaurantId(restaurantId)).thenReturn(Optional.of(tumkurPickup));
        when(locationZoneRepository.findAll()).thenReturn(List.of(tumkurZone, bangaloreZone));

        // Partner 1: in Bangalore zone (should be SKIPPED)
        DeliveryPartner blrPartner = DeliveryPartner.create(UUID.randomUUID(), "Bangalore Partner", VehicleType.BIKE, "KA01AB1234");
        setId(blrPartner, UUID.randomUUID());
        blrPartner.verifyKyc();
        blrPartner.setOnline(true);
        blrPartner.verifyZone("dz-bangalore", "Bangalore Zone", 12.9716, 77.5946);

        // Partner 2: in Tumkur zone (should be MATCHED and ASSIGNED)
        DeliveryPartner tumkurPartner = DeliveryPartner.create(UUID.randomUUID(), "Tumkur Partner", VehicleType.BIKE, "KA06CD5678");
        setId(tumkurPartner, UUID.randomUUID());
        tumkurPartner.verifyKyc();
        tumkurPartner.setOnline(true);
        tumkurPartner.verifyZone("dz-tumkur", "Tumkur Zone", 13.34088, 77.10066);

        // Partner 3: no zone selected / unzoned (should be SKIPPED)
        DeliveryPartner unzonedPartner = DeliveryPartner.create(UUID.randomUUID(), "Unzoned Partner", VehicleType.BIKE, "KA02EF9012");
        setId(unzonedPartner, UUID.randomUUID());
        unzonedPartner.verifyKyc();
        unzonedPartner.setOnline(true);

        when(deliveryPartnerRepository.findByOnlineTrueOrderByUpdatedAtDesc())
                .thenReturn(List.of(blrPartner, unzonedPartner, tumkurPartner));

        deliveryService.createAssignmentForOrder(orderId);

        // Verify assignment created specifically for Tumkur partner
        ArgumentCaptor<DeliveryAssignment> captor = ArgumentCaptor.forClass(DeliveryAssignment.class);
        verify(deliveryAssignmentRepository).save(captor.capture());
        DeliveryAssignment savedAssignment = captor.getValue();

        assertThat(savedAssignment.getDeliveryPartner().getFullName()).isEqualTo("Tumkur Partner");
        assertThat(savedAssignment.getDeliveryPartner().getZoneId()).isEqualTo("dz-tumkur");
    }

    @Test
    @DisplayName("When no delivery partner in the matching zone is available, order is not assigned to other zones")
    void whenNoPartnerInSameZoneAvailable_orderNotAssignedToOtherZones() {
        // Restaurant is in Tumkur zone
        RestaurantPickupQuery.PickupLocation tumkurPickup = new RestaurantPickupQuery.PickupLocation(
                restaurantId,
                "Tumkur Biryani",
                "MG Road, Tumkur",
                null,
                "Tumkur",
                "572101",
                BigDecimal.valueOf(13.34088),
                BigDecimal.valueOf(77.10066),
                "dz-tumkur",
                "Tumkur Zone"
        );
        when(restaurantPickupQuery.findByRestaurantId(restaurantId)).thenReturn(Optional.of(tumkurPickup));
        when(locationZoneRepository.findAll()).thenReturn(List.of(tumkurZone, bangaloreZone));

        // Only Bangalore partners are online
        DeliveryPartner blrPartner = DeliveryPartner.create(UUID.randomUUID(), "Bangalore Partner", VehicleType.BIKE, "KA01AB1234");
        setId(blrPartner, UUID.randomUUID());
        blrPartner.verifyKyc();
        blrPartner.setOnline(true);
        blrPartner.verifyZone("dz-bangalore", "Bangalore Zone", 12.9716, 77.5946);

        when(deliveryPartnerRepository.findByOnlineTrueOrderByUpdatedAtDesc())
                .thenReturn(List.of(blrPartner));

        deliveryService.createAssignmentForOrder(orderId);

        // Should NOT save an assignment to blrPartner
        verify(deliveryAssignmentRepository, never()).save(any());
        // Order status set to WAITING_FOR_DELIVERY_PARTNER
        verify(orderDeliveryPort).updateStatus(orderId, OrderStatus.WAITING_FOR_DELIVERY_PARTNER);
    }

    @Test
    @DisplayName("Bangalore restaurant assigns to Bangalore delivery partner and skips Tumkur partner")
    void bangaloreRestaurantAssignsToBangalorePartner() {
        RestaurantPickupQuery.PickupLocation blrPickup = new RestaurantPickupQuery.PickupLocation(
                restaurantId,
                "Koramangala Diner",
                "Indiranagar, Bangalore",
                null,
                "Bengaluru",
                "560038",
                BigDecimal.valueOf(12.9716),
                BigDecimal.valueOf(77.5946),
                "dz-bangalore",
                "Bangalore Zone"
        );
        when(restaurantPickupQuery.findByRestaurantId(restaurantId)).thenReturn(Optional.of(blrPickup));
        when(locationZoneRepository.findAll()).thenReturn(List.of(tumkurZone, bangaloreZone));

        DeliveryPartner tumkurPartner = DeliveryPartner.create(UUID.randomUUID(), "Tumkur Partner", VehicleType.BIKE, "KA06CD5678");
        setId(tumkurPartner, UUID.randomUUID());
        tumkurPartner.verifyKyc();
        tumkurPartner.setOnline(true);
        tumkurPartner.verifyZone("dz-tumkur", "Tumkur Zone", 13.34088, 77.10066);

        DeliveryPartner blrPartner = DeliveryPartner.create(UUID.randomUUID(), "Bangalore Partner", VehicleType.BIKE, "KA01AB1234");
        setId(blrPartner, UUID.randomUUID());
        blrPartner.verifyKyc();
        blrPartner.setOnline(true);
        blrPartner.verifyZone("dz-bangalore", "Bangalore Zone", 12.9716, 77.5946);

        when(deliveryPartnerRepository.findByOnlineTrueOrderByUpdatedAtDesc())
                .thenReturn(List.of(tumkurPartner, blrPartner));

        deliveryService.createAssignmentForOrder(orderId);

        ArgumentCaptor<DeliveryAssignment> captor = ArgumentCaptor.forClass(DeliveryAssignment.class);
        verify(deliveryAssignmentRepository).save(captor.capture());
        DeliveryAssignment savedAssignment = captor.getValue();

        assertThat(savedAssignment.getDeliveryPartner().getFullName()).isEqualTo("Bangalore Partner");
        assertThat(savedAssignment.getDeliveryPartner().getZoneId()).isEqualTo("dz-bangalore");
    }

    private static void setId(Object entity, UUID id) {
        try {
            java.lang.reflect.Field field = com.foodie.common.entity.BaseEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
