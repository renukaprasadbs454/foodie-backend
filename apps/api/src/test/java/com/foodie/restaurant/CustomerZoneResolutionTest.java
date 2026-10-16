package com.foodie.restaurant;

import com.foodie.admin.entity.LocationZone;
import com.foodie.admin.repository.LocationZoneRepository;
import com.foodie.common.enums.RestaurantStatus;
import com.foodie.common.enums.RestaurantType;
import com.foodie.restaurant.dto.response.CustomerZoneDetectResponseDto;
import com.foodie.restaurant.entity.Restaurant;
import com.foodie.restaurant.entity.RestaurantAddress;
import com.foodie.restaurant.mapper.RestaurantMapper;
import com.foodie.restaurant.repository.RestaurantAddressRepository;
import com.foodie.restaurant.repository.RestaurantLegalDetailRepository;
import com.foodie.restaurant.repository.RestaurantRepository;
import com.foodie.restaurant.service.RestaurantCacheService;
import com.foodie.restaurant.service.RestaurantService.PageResult;
import com.foodie.restaurant.service.impl.RestaurantServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerZoneResolutionTest {

    @Mock private RestaurantRepository restaurantRepository;
    @Mock private RestaurantAddressRepository restaurantAddressRepository;
    @Mock private RestaurantLegalDetailRepository restaurantLegalDetailRepository;
    @Mock private LocationZoneRepository locationZoneRepository;
    @Mock private RestaurantCacheService restaurantCacheService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private RestaurantServiceImpl service;

    private LocationZone bangaloreZone;
    private LocationZone tumkurZone;

    private Restaurant bangaloreRestaurant;
    private Restaurant tumkurRestaurant;

    @BeforeEach
    void setUp() {
        RestaurantMapper mapper = new RestaurantMapper();
        ObjectMapper objectMapper = new ObjectMapper();

        service = new RestaurantServiceImpl(
                restaurantRepository,
                restaurantAddressRepository,
                null,
                restaurantLegalDetailRepository,
                null,
                null,
                null,
                mapper,
                null,
                restaurantCacheService,
                eventPublisher,
                objectMapper,
                locationZoneRepository,
                null,
                new BigDecimal("10.00")
        );

        bangaloreZone = new LocationZone();
        bangaloreZone.setId("dz-bangalore");
        bangaloreZone.setZoneName("Bangalore Zone");
        bangaloreZone.setCityName("Bangalore");
        bangaloreZone.setLatitude(12.9716);
        bangaloreZone.setLongitude(77.5946);
        bangaloreZone.setRadiusKm(25.0);
        bangaloreZone.setStatus("ACTIVE");
        bangaloreZone.setRestaurantEnabled(true);
        bangaloreZone.setCustomerOrderingEnabled(true);

        tumkurZone = new LocationZone();
        tumkurZone.setId("dz-tumkur");
        tumkurZone.setZoneName("Tumkur Zone");
        tumkurZone.setCityName("Tumkur");
        tumkurZone.setLatitude(13.3379);
        tumkurZone.setLongitude(77.1173);
        tumkurZone.setRadiusKm(20.0);
        tumkurZone.setStatus("ACTIVE");
        tumkurZone.setRestaurantEnabled(true);
        tumkurZone.setCustomerOrderingEnabled(true);

        RestaurantAddress blrAddress = RestaurantAddress.create(
                "100 Feet Rd", "Indiranagar", "Bangalore", "560038",
                new BigDecimal("12.978400"), new BigDecimal("77.640800")
        );
        bangaloreRestaurant = Restaurant.createPending(
                UUID.randomUUID(), "Spice Garden Bangalore", "Biryani & Curries",
                new String[]{"Indian"}, RestaurantType.BOTH, blrAddress, new BigDecimal("10.00")
        );
        bangaloreRestaurant.approve();

        RestaurantAddress tmkAddress = RestaurantAddress.create(
                "MG Road", "Near Bus Stand", "Tumkur", "572101",
                new BigDecimal("13.364602"), new BigDecimal("77.160600")
        );
        tumkurRestaurant = Restaurant.createPending(
                UUID.randomUUID(), "Meghana Foods Tumkur", "Authentic Biryani",
                new String[]{"Indian"}, RestaurantType.BOTH, tmkAddress, new BigDecimal("10.00")
        );
        tumkurRestaurant.approve();
    }

    @Test
    void detectCustomerZone_whenInTumkur_identifiesTumkurZone() {
        when(locationZoneRepository.findAll()).thenReturn(List.of(bangaloreZone, tumkurZone));
        when(restaurantRepository.findAllByStatus(RestaurantStatus.APPROVED))
                .thenReturn(List.of(bangaloreRestaurant, tumkurRestaurant));

        // Customer in Tumkur (approx 3km from Tumkur zone center)
        CustomerZoneDetectResponseDto response = service.detectCustomerZone(13.3400, 77.1200);

        assertThat(response.serviceable()).isTrue();
        assertThat(response.zoneId()).isEqualTo("dz-tumkur");
        assertThat(response.zoneName()).isEqualTo("Tumkur Zone");
        assertThat(response.cityName()).isEqualTo("Tumkur");
        assertThat(response.restaurantCount()).isEqualTo(1);
    }

    @Test
    void detectCustomerZone_whenInBangalore_identifiesBangaloreZone() {
        when(locationZoneRepository.findAll()).thenReturn(List.of(bangaloreZone, tumkurZone));
        when(restaurantRepository.findAllByStatus(RestaurantStatus.APPROVED))
                .thenReturn(List.of(bangaloreRestaurant, tumkurRestaurant));

        // Customer in Bangalore (approx 4km from Bangalore zone center)
        CustomerZoneDetectResponseDto response = service.detectCustomerZone(12.9750, 77.6000);

        assertThat(response.serviceable()).isTrue();
        assertThat(response.zoneId()).isEqualTo("dz-bangalore");
        assertThat(response.zoneName()).isEqualTo("Bangalore Zone");
        assertThat(response.cityName()).isEqualTo("Bangalore");
        assertThat(response.restaurantCount()).isEqualTo(1);
    }

    @Test
    void detectCustomerZone_whenOutsideAllZones_returnsUnserviceable() {
        when(locationZoneRepository.findAll()).thenReturn(List.of(bangaloreZone, tumkurZone));

        // Customer in Delhi
        CustomerZoneDetectResponseDto response = service.detectCustomerZone(28.6139, 77.2090);

        assertThat(response.serviceable()).isFalse();
        assertThat(response.zoneId()).isNull();
        assertThat(response.availableZones()).contains("Bangalore Zone", "Tumkur Zone");
    }

    @Test
    void searchRestaurants_inTumkur_returnsOnlyTumkurRestaurants() {
        when(locationZoneRepository.findAll()).thenReturn(List.of(bangaloreZone, tumkurZone));
        when(restaurantRepository.findAllByStatus(RestaurantStatus.APPROVED))
                .thenReturn(List.of(bangaloreRestaurant, tumkurRestaurant));

        // Search with Tumkur coordinates
        var result = service.search(null, null, null, 13.3400, 77.1200, null, 0, 20, null);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).name()).isEqualTo("Meghana Foods Tumkur");
    }

    @Test
    void searchRestaurants_inBangalore_returnsOnlyBangaloreRestaurants() {
        when(locationZoneRepository.findAll()).thenReturn(List.of(bangaloreZone, tumkurZone));
        when(restaurantRepository.findAllByStatus(RestaurantStatus.APPROVED))
                .thenReturn(List.of(bangaloreRestaurant, tumkurRestaurant));

        // Search with Bangalore coordinates
        var result = service.search(null, null, null, 12.9750, 77.6000, null, 0, 20, null);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).name()).isEqualTo("Spice Garden Bangalore");
    }

    @Test
    void searchRestaurants_outsideAnyZone_returnsEmptyFeed() {
        when(locationZoneRepository.findAll()).thenReturn(List.of(bangaloreZone, tumkurZone));

        // Search with coordinates in Mumbai / Delhi
        var result = service.search(null, null, null, 19.0760, 72.8777, null, 0, 20, null);

        assertThat(result.items()).isEmpty();
        assertThat(result.pagination().totalElements()).isEqualTo(0);
    }
}
