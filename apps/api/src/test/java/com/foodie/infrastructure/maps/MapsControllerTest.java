package com.foodie.infrastructure.maps;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodie.common.exception.GlobalExceptionHandler;
import com.foodie.infrastructure.maps.controller.MapsController;
import com.foodie.infrastructure.maps.dto.response.GeocodeResponseDto;
import com.foodie.infrastructure.maps.dto.response.ReverseGeocodeResponseDto;
import com.foodie.infrastructure.maps.service.MapsService;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class MapsControllerTest {

    @Mock
    private MapsService mapsService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MapsController controller = new MapsController(mapsService, null);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void geocode_validAddress_returnsSuccess() throws Exception {
        GeocodeResponseDto response = new GeocodeResponseDto(
                new BigDecimal("12.9166"),
                new BigDecimal("77.6101"),
                "BTM Layout, Bengaluru, Karnataka, India",
                "Bengaluru",
                "Karnataka",
                "India",
                "560076",
                "BTM Layout"
        );
        when(mapsService.geocode("BTM Layout Bangalore")).thenReturn(response);

        mockMvc.perform(get("/api/v1/maps/geocode")
                        .param("address", "BTM Layout Bangalore"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.latitude").value(12.9166))
                .andExpect(jsonPath("$.data.longitude").value(77.6101))
                .andExpect(jsonPath("$.data.formattedAddress").value("BTM Layout, Bengaluru, Karnataka, India"))
                .andExpect(jsonPath("$.data.city").value("Bengaluru"));
    }

    @Test
    void reverseGeocode_validCoordinates_returnsSuccess() throws Exception {
        BigDecimal lat = new BigDecimal("12.9716");
        BigDecimal lng = new BigDecimal("77.5946");
        ReverseGeocodeResponseDto response = new ReverseGeocodeResponseDto(
                lat,
                lng,
                "Bengaluru, Karnataka, India",
                "Bengaluru",
                "Karnataka",
                "India",
                "560001",
                "Central Bengaluru"
        );
        when(mapsService.reverseGeocode(lat, lng)).thenReturn(response);

        mockMvc.perform(get("/api/v1/maps/reverse-geocode")
                        .param("latitude", "12.9716")
                        .param("longitude", "77.5946"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.latitude").value(12.9716))
                .andExpect(jsonPath("$.data.longitude").value(77.5946))
                .andExpect(jsonPath("$.data.formattedAddress").value("Bengaluru, Karnataka, India"));
    }

    @Test
    void reverseGeocode_invalidCoordinates_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/maps/reverse-geocode")
                        .param("latitude", "999.0")
                        .param("longitude", "77.5946"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
    }

    @Test
    void validateCoordinate_valid_returnsTrue() throws Exception {
        mockMvc.perform(get("/api/v1/maps/validate-coordinate")
                        .param("latitude", "12.9716")
                        .param("longitude", "77.5946"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.valid").value(true))
                .andExpect(jsonPath("$.data.latitude").value(12.9716))
                .andExpect(jsonPath("$.data.longitude").value(77.5946));
    }

    @Test
    void validateCoordinate_invalid_returnsFalse() throws Exception {
        mockMvc.perform(get("/api/v1/maps/validate-coordinate")
                        .param("latitude", "999.0")
                        .param("longitude", "77.5946"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.valid").value(false));
    }
}
