package com.foodie.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodie.admin.controller.AdminDeliveryController;
import com.foodie.admin.security.AdminAccess;
import com.foodie.admin.service.AdminOperationsService;
import com.foodie.common.dto.PaginationMeta;
import com.foodie.common.enums.UserType;
import com.foodie.delivery.dto.response.AdminDeliveryPartnerResponseDto;
import com.foodie.security.jwt.JwtTokenProvider;
import com.foodie.security.principal.AuthPrincipal;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminDeliveryController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminDeliveryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminOperationsService adminOperationsService;

    @MockBean
    private AdminAccess adminAccess;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void list_returns200() throws Exception {
        UUID partnerId = UUID.randomUUID();
        UUID credentialId = UUID.randomUUID();
        AdminDeliveryPartnerResponseDto item = new AdminDeliveryPartnerResponseDto(
                partnerId,
                credentialId,
                "Test Partner",
                "+919876543210",
                "BIKE",
                "KA01AB1234",
                null,
                "VERIFIED",
                true,
                BigDecimal.ZERO,
                10L,
                "Downtown",
                List.of(),
                Instant.now()
        );
        AdminOperationsService.PageResult<AdminDeliveryPartnerResponseDto> pageResult =
                new AdminOperationsService.PageResult<>(List.of(item), new PaginationMeta(0, 20, 1, 1));

        when(adminOperationsService.listDeliveryPartners(any(), any(), any(), anyInt(), anyInt(), anyString()))
                .thenReturn(pageResult);

        mockMvc.perform(get("/api/v1/admin/delivery-partners")
                        .principal(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                                new AuthPrincipal(credentialId, UserType.ADMIN), null, List.of())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items[0].fullName").value("Test Partner"));
    }
}
