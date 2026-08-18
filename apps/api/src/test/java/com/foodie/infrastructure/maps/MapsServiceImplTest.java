package com.foodie.infrastructure.maps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodie.common.exception.BadRequestException;
import com.foodie.infrastructure.maps.config.MapsProperties;
import com.foodie.infrastructure.maps.dto.response.GeocodeResponseDto;
import com.foodie.infrastructure.maps.dto.response.ReverseGeocodeResponseDto;
import com.foodie.infrastructure.maps.service.impl.MapsServiceImpl;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class MapsServiceImplTest {

    private MapsProperties mapsProperties;
    private ObjectMapper objectMapper;

    @Mock
    private org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private MapsServiceImpl mapsService;

    @BeforeEach
    void setUp() {
        mapsProperties = new MapsProperties();
        mapsProperties.setApiKey("stub"); // Stub mode
        objectMapper = new ObjectMapper();

        mapsService = new MapsServiceImpl(mapsProperties, objectMapper, redisTemplate);
    }

    @Test
    void geocode_blankAddress_throwsBadRequest() {
        assertThatThrownBy(() -> mapsService.geocode("   "))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("must not be blank");
    }

    @Test
    void geocode_stubMode_returnsMockResponse() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);

        GeocodeResponseDto result = mapsService.geocode("BTM Layout Bangalore");

        assertThat(result).isNotNull();
        assertThat(result.latitude()).isEqualTo(new BigDecimal("12.971600"));
        assertThat(result.longitude()).isEqualTo(new BigDecimal("77.594600"));
        assertThat(result.formattedAddress()).contains("BTM Layout Bangalore");
        assertThat(result.city()).isEqualTo("Bengaluru");
    }

    @Test
    void reverseGeocode_validCoordinates_returnsMockResponse() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);

        BigDecimal lat = new BigDecimal("12.9166");
        BigDecimal lng = new BigDecimal("77.6101");

        ReverseGeocodeResponseDto result = mapsService.reverseGeocode(lat, lng);

        assertThat(result).isNotNull();
        assertThat(result.latitude()).isEqualTo(lat);
        assertThat(result.longitude()).isEqualTo(lng);
        assertThat(result.city()).isEqualTo("Bengaluru");
        assertThat(result.state()).isEqualTo("Karnataka");
    }

    @Test
    void reverseGeocode_invalidCoordinates_throwsBadRequest() {
        BigDecimal lat = new BigDecimal("100.0000");
        BigDecimal lng = new BigDecimal("77.6101");

        assertThatThrownBy(() -> mapsService.reverseGeocode(lat, lng))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Latitude must be between -90 and 90");
    }

    @Test
    void calculateDistanceKm_correctDistance() {
        BigDecimal lat1 = new BigDecimal("12.9716"); // Bengaluru
        BigDecimal lng1 = new BigDecimal("77.5946");
        BigDecimal lat2 = new BigDecimal("13.0827"); // Chennai (~290 km)
        BigDecimal lng2 = new BigDecimal("80.2707");

        double distance = mapsService.calculateDistanceKm(lat1, lng1, lat2, lng2);

        assertThat(distance).isGreaterThan(280.0).isLessThan(300.0);
    }
}
