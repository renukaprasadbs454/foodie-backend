package com.foodie.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodie.admin.controller.AdminZoneController;
import com.foodie.admin.dto.ActiveOperatingZoneDto;
import com.foodie.admin.dto.AdminZoneLoginHistoryDto;
import com.foodie.admin.dto.ZoneSelectionRequestDto;
import com.foodie.admin.dto.ZoneSelectionResponseDto;
import com.foodie.admin.entity.LocationZone;
import com.foodie.admin.service.AdminZoneService;
import com.foodie.common.enums.UserType;
import com.foodie.security.principal.AuthPrincipal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminZoneControllerTest {

    @Mock private AdminZoneService adminZoneService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UUID adminUserId = UUID.fromString("33333333-3333-3333-3333-333333333001");

    @BeforeEach
    void setUp() {
        AdminZoneController controller = new AdminZoneController(adminZoneService);

        HandlerMethodArgumentResolver authPrincipalResolver = new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
            }

            @Override
            public Object resolveArgument(
                    MethodParameter parameter,
                    ModelAndViewContainer mavContainer,
                    NativeWebRequest webRequest,
                    WebDataBinderFactory binderFactory
            ) {
                return new AuthPrincipal(adminUserId, UserType.ADMIN);
            }
        };

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(authPrincipalResolver)
                .build();
    }

    @Test
    void getAvailableZones_returns200AndList() throws Exception {
        ActiveOperatingZoneDto z1 = new ActiveOperatingZoneDto(
                "dz-bangalore", "Bangalore Zone", "Bangalore", 12.9716, 77.5946, 25.0, "ACTIVE", true, true, true, 45
        );
        ActiveOperatingZoneDto z2 = new ActiveOperatingZoneDto(
                "dz-tumkur", "Tumkur Zone", "Tumkur", 13.3379, 77.1173, 20.0, "ACTIVE", true, true, true, 22
        );

        when(adminZoneService.getAvailableZones(adminUserId)).thenReturn(List.of(z1, z2));

        mockMvc.perform(get("/api/v1/admin/zones/available"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value("dz-bangalore"))
                .andExpect(jsonPath("$.data[0].zoneName").value("Bangalore Zone"))
                .andExpect(jsonPath("$.data[1].id").value("dz-tumkur"));
    }

    @Test
    void selectZone_validRequest_returns200AndSelectedZoneContext() throws Exception {
        ZoneSelectionResponseDto resp = new ZoneSelectionResponseDto(
                UUID.randomUUID(),
                "dz-bangalore",
                "Bangalore Zone",
                "Bangalore",
                "Super Admin",
                "admin@foodie.local",
                "SUPER_ADMIN",
                Instant.now(),
                "ACTIVE"
        );

        when(adminZoneService.selectZone(eq(adminUserId), eq("dz-bangalore"), anyString(), any()))
                .thenReturn(resp);

        ZoneSelectionRequestDto body = new ZoneSelectionRequestDto("dz-bangalore");

        mockMvc.perform(post("/api/v1/admin/zones/select")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.zoneId").value("dz-bangalore"))
                .andExpect(jsonPath("$.data.zoneName").value("Bangalore Zone"))
                .andExpect(jsonPath("$.data.role").value("SUPER_ADMIN"));
    }

    @Test
    void selectZone_blankZoneId_returns400BadRequest() throws Exception {
        ZoneSelectionRequestDto body = new ZoneSelectionRequestDto("");

        mockMvc.perform(post("/api/v1/admin/zones/select")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getLoginHistory_returns200AndPagedRecords() throws Exception {
        AdminZoneLoginHistoryDto item = new AdminZoneLoginHistoryDto(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Super Admin",
                "admin@foodie.local",
                "SUPER_ADMIN",
                "dz-bangalore",
                "Bangalore Zone",
                Instant.now(),
                "ACTIVE",
                "127.0.0.1",
                "Chrome"
        );

        when(adminZoneService.getLoginHistory(eq("dz-bangalore"), eq("admin"), anyInt(), anyInt()))
                .thenReturn(new PageImpl<>(List.of(item)));

        mockMvc.perform(get("/api/v1/admin/zones/history")
                        .param("zoneId", "dz-bangalore")
                        .param("search", "admin")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].zoneName").value("Bangalore Zone"))
                .andExpect(jsonPath("$.data[0].adminEmail").value("admin@foodie.local"))
                .andExpect(jsonPath("$.meta.pagination.totalElements").value(1));
    }

    @Test
    void validateZone_validZone_returns200() throws Exception {
        LocationZone zone = new LocationZone();
        zone.setId("dz-bangalore");
        zone.setZoneName("Bangalore Zone");
        zone.setStatus("ACTIVE");

        when(adminZoneService.validateZone("dz-bangalore")).thenReturn(zone);

        mockMvc.perform(get("/api/v1/admin/zones/validate").param("zoneId", "dz-bangalore"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value("Zone valid and active: Bangalore Zone"));
    }
}
