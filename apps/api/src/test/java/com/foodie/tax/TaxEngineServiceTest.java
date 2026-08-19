package com.foodie.tax;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

import com.foodie.tax.entity.TaxRule;
import com.foodie.tax.entity.TaxSnapshot;
import com.foodie.tax.enums.PricingComponentType;
import com.foodie.tax.enums.TaxType;
import com.foodie.tax.model.TaxCalculationRequest;
import com.foodie.tax.model.TaxCalculationResult;
import com.foodie.tax.model.TaxComponentInput;
import com.foodie.tax.model.TaxContext;
import com.foodie.tax.repository.TaxSnapshotItemRepository;
import com.foodie.tax.repository.TaxSnapshotRepository;
import com.foodie.tax.service.PaiseRoundingPolicy;
import com.foodie.tax.service.RoundingPolicy;
import com.foodie.tax.service.TaxEngineServiceImpl;
import com.foodie.tax.service.TaxRuleResolver;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaxEngineServiceTest {

    @Mock private TaxRuleResolver taxRuleResolver;
    @Mock private TaxSnapshotRepository taxSnapshotRepository;
    @Mock private TaxSnapshotItemRepository taxSnapshotItemRepository;

    private RoundingPolicy roundingPolicy;
    private TaxEngineServiceImpl taxEngineService;

    private final UUID orderId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        roundingPolicy = new PaiseRoundingPolicy();
        taxEngineService = new TaxEngineServiceImpl(
                taxRuleResolver,
                roundingPolicy,
                taxSnapshotRepository,
                taxSnapshotItemRepository
        );

        Mockito.lenient().when(taxSnapshotRepository.save(any(TaxSnapshot.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void calculateAndSnapshot_intraStateFoodAndDelivery_computesCorrectComponentTaxes() {
        TaxRule foodRule = TaxRule.create(
                "Food Intra GST",
                PricingComponentType.FOOD,
                "RESTAURANT_FOOD",
                TaxType.CGST_SGST,
                new BigDecimal("0.0250"),
                new BigDecimal("0.0250"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                LocalDate.now().minusDays(10),
                null,
                1,
                1
        );

        TaxRule deliveryRule = TaxRule.create(
                "Delivery Intra GST",
                PricingComponentType.DELIVERY,
                "SERVICE_DELIVERY",
                TaxType.CGST_SGST,
                new BigDecimal("0.0900"),
                new BigDecimal("0.0900"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                LocalDate.now().minusDays(10),
                null,
                1,
                1
        );

        TaxContext context = TaxContext.intraState("KA");

        Mockito.when(taxRuleResolver.resolveRule(Mockito.eq(PricingComponentType.FOOD), any(), any()))
                .thenReturn(Optional.of(foodRule));
        Mockito.when(taxRuleResolver.resolveRule(Mockito.eq(PricingComponentType.DELIVERY), any(), any()))
                .thenReturn(Optional.of(deliveryRule));

        // Gross food: 50000 paise (Rs 500), Discount: 5000 paise (Rs 50) -> Taxable: 45000 paise
        // Food Tax: 2.5% CGST (1125 paise) + 2.5% SGST (1125 paise) = 2250 paise
        // Delivery: 4000 paise (Rs 40), Discount: 0 -> Taxable: 4000 paise
        // Delivery Tax: 9% CGST (360 paise) + 9% SGST (360 paise) = 720 paise
        List<TaxComponentInput> components = List.of(
                new TaxComponentInput(PricingComponentType.FOOD, "Food items", 50000L, 5000L),
                new TaxComponentInput(PricingComponentType.DELIVERY, "Delivery fee", 4000L, 0L)
        );

        TaxCalculationRequest request = new TaxCalculationRequest(
                orderId,
                null,
                null,
                context,
                components,
                List.of()
        );

        TaxCalculationResult result = taxEngineService.calculateAndSnapshot(request);

        assertThat(result.totalTaxablePaise()).isEqualTo(49000L);
        assertThat(result.totalCgstPaise()).isEqualTo(1485L); // 1125 + 360
        assertThat(result.totalSgstPaise()).isEqualTo(1485L); // 1125 + 360
        assertThat(result.totalIgstPaise()).isEqualTo(0L);
        assertThat(result.totalTaxPaise()).isEqualTo(2970L); // 2250 + 720
        assertThat(result.grandTotalPaise()).isEqualTo(51970L); // 49000 + 2970
    }

    @Test
    void calculateAndSnapshot_interState_computesIgst() {
        TaxRule igstFoodRule = TaxRule.create(
                "Food Inter GST",
                PricingComponentType.FOOD,
                "RESTAURANT_FOOD",
                TaxType.IGST,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("0.0500"),
                BigDecimal.ZERO,
                LocalDate.now().minusDays(10),
                null,
                1,
                1
        );

        TaxContext context = TaxContext.interState("KA", "MH");

        Mockito.when(taxRuleResolver.resolveRule(Mockito.eq(PricingComponentType.FOOD), any(), any()))
                .thenReturn(Optional.of(igstFoodRule));

        List<TaxComponentInput> components = List.of(
                new TaxComponentInput(PricingComponentType.FOOD, "Food items", 100000L, 0L)
        );

        TaxCalculationRequest request = new TaxCalculationRequest(
                orderId,
                null,
                null,
                context,
                components,
                List.of()
        );

        TaxCalculationResult result = taxEngineService.calculateAndSnapshot(request);

        assertThat(result.totalTaxablePaise()).isEqualTo(100000L);
        assertThat(result.totalCgstPaise()).isEqualTo(0L);
        assertThat(result.totalSgstPaise()).isEqualTo(0L);
        assertThat(result.totalIgstPaise()).isEqualTo(5000L);
        assertThat(result.totalTaxPaise()).isEqualTo(5000L);
        assertThat(result.grandTotalPaise()).isEqualTo(105000L);
    }
}
