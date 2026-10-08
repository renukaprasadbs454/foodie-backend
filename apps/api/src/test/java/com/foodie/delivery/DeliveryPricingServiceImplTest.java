package com.foodie.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodie.delivery.dto.request.UpdateDeliveryPricingRequestDto;
import com.foodie.delivery.dto.response.DeliveryPricingConfigResponseDto;
import com.foodie.delivery.entity.DeliveryPricingConfig;
import com.foodie.delivery.repository.DeliveryPricingConfigRepository;
import com.foodie.delivery.service.impl.DeliveryPricingServiceImpl;
import com.foodie.shared.contract.AdminIdentityQueryPort;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeliveryPricingServiceImplTest {

    @Mock
    private DeliveryPricingConfigRepository configRepository;

    @Mock
    private AdminIdentityQueryPort adminIdentityQueryPort;

    private DeliveryPricingServiceImpl pricingService;
    private DeliveryPricingConfig defaultConfig;

    @BeforeEach
    void setUp() {
        pricingService = new DeliveryPricingServiceImpl(configRepository, new ObjectMapper(), adminIdentityQueryPort);
        defaultConfig = DeliveryPricingConfig.createDefault();
    }

    @Test
    void calculateDeliveryFee_returnsMinPrice_whenDistanceRateIsSmaller() {
        when(configRepository.findById(any())).thenReturn(Optional.of(defaultConfig));

        // 2 km @ 25/km = 50 < min price 120 -> should return 120.00
        BigDecimal fee = pricingService.calculateDeliveryFee(2.0);

        assertThat(fee).isEqualByComparingTo("120.00");
    }

    @Test
    void calculateDeliveryFee_returnsPerKmPrice_whenDistanceRateIsGreater() {
        when(configRepository.findById(any())).thenReturn(Optional.of(defaultConfig));

        // 10 km @ 25/km = 250 > min price 200 -> should return 250.00
        BigDecimal fee = pricingService.calculateDeliveryFee(10.0);

        assertThat(fee).isEqualByComparingTo("250.00");
    }

    @Test
    void updatePricingConfig_updatesAndReturnsNewConfigWithResolvedAdminUserId() {
        when(configRepository.findById(any())).thenReturn(Optional.of(defaultConfig));
        when(configRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        UUID userCredentialId = UUID.fromString("33333333-3333-3333-3333-333333333001");
        UUID resolvedAdminUserId = UUID.fromString("44444444-4444-4444-4444-444444444001");

        when(adminIdentityQueryPort.findAdminUserIdByUserCredentialId(userCredentialId))
                .thenReturn(Optional.of(resolvedAdminUserId));

        UpdateDeliveryPricingRequestDto request = new UpdateDeliveryPricingRequestDto(
                new BigDecimal("40.00"),
                new BigDecimal("12.50")
        );

        DeliveryPricingConfigResponseDto updated = pricingService.updatePricingConfig(userCredentialId, request);

        assertThat(updated.minPricePerDelivery()).isEqualByComparingTo("40.00");
        assertThat(updated.moneyPerKm()).isEqualByComparingTo("12.50");
        assertThat(updated.updatedBy()).isEqualTo(resolvedAdminUserId);
    }
}
