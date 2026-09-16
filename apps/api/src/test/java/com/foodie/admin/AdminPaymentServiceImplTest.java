package com.foodie.admin;

import com.foodie.admin.dto.request.CommissionConfigDto;
import com.foodie.admin.dto.response.PaymentSplitBreakdownDto;
import com.foodie.admin.service.impl.AdminPaymentServiceImpl;
import com.foodie.delivery.repository.DeliveryPartnerRepository;
import com.foodie.order.repository.OrderRepository;
import com.foodie.payment.repository.OrderSettlementRepository;
import com.foodie.payment.repository.PaymentRepository;
import com.foodie.payment.service.SettlementService;
import com.foodie.restaurant.repository.RestaurantRepository;
import com.foodie.wallet.repository.LedgerEntryRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminPaymentServiceImplTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private RestaurantRepository restaurantRepository;
    @Mock
    private DeliveryPartnerRepository deliveryPartnerRepository;
    @Mock
    private OrderSettlementRepository settlementRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private LedgerEntryRepository ledgerEntryRepository;
    @Mock
    private SettlementService settlementService;

    private AdminPaymentServiceImpl paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new AdminPaymentServiceImpl(
                orderRepository,
                restaurantRepository,
                deliveryPartnerRepository,
                settlementRepository,
                paymentRepository,
                ledgerEntryRepository,
                settlementService
        );
    }

    @Test
    @DisplayName("Default commission rules match 14% rest, 10% delivery, and 40 fixed fee")
    void defaultCommissionRules() {
        CommissionConfigDto rules = paymentService.getCommissionRules();
        assertThat(rules.restaurantCommissionRate()).isEqualByComparingTo("14.00");
        assertThat(rules.deliveryCommissionRate()).isEqualByComparingTo("10.00");
        assertThat(rules.platformFixedFee()).isEqualByComparingTo("40.00");
    }

    @Test
    @DisplayName("Calculate payment split via settlementService")
    void calculateSplitConservesMoney() {
        BigDecimal foodSubtotal = new BigDecimal("450.00");
        BigDecimal deliveryFee = new BigDecimal("90.00");

        when(settlementService.calculateSplit(any(), any())).thenReturn(
                new SettlementService.SplitBreakdownDto(
                        new BigDecimal("580.00"),
                        foodSubtotal,
                        deliveryFee,
                        new BigDecimal("40.00"),
                        new BigDecimal("14.00"),
                        new BigDecimal("63.00"),
                        new BigDecimal("387.00"),
                        new BigDecimal("10.00"),
                        new BigDecimal("9.00"),
                        new BigDecimal("81.00"),
                        new BigDecimal("112.00")
                )
        );

        PaymentSplitBreakdownDto split = paymentService.calculateSplit(foodSubtotal, deliveryFee);

        assertThat(split.totalPaid()).isEqualByComparingTo("580.00");
        assertThat(split.adminFoodCommission()).isEqualByComparingTo("63.00");
        assertThat(split.adminDeliveryCommission()).isEqualByComparingTo("9.00");
        assertThat(split.platformFee()).isEqualByComparingTo("40.00");
        assertThat(split.adminTotalRevenue()).isEqualByComparingTo("112.00");
        assertThat(split.restaurantNetShare()).isEqualByComparingTo("387.00");
        assertThat(split.deliveryPartnerNetShare()).isEqualByComparingTo("81.00");

        BigDecimal sumDistributed = split.adminTotalRevenue()
                .add(split.restaurantNetShare())
                .add(split.deliveryPartnerNetShare());

        assertThat(sumDistributed).isEqualByComparingTo(split.totalPaid());
    }

    @Test
    @DisplayName("Updating commission rules dynamically adjusts split calculations")
    void updateCommissionRules() {
        CommissionConfigDto newRules = new CommissionConfigDto(
                new BigDecimal("20.00"),
                new BigDecimal("5.00"),
                new BigDecimal("50.00"));

        paymentService.updateCommissionRules(newRules);

        CommissionConfigDto updated = paymentService.getCommissionRules();
        assertThat(updated.restaurantCommissionRate()).isEqualByComparingTo("20.00");
        assertThat(updated.deliveryCommissionRate()).isEqualByComparingTo("5.00");
        assertThat(updated.platformFixedFee()).isEqualByComparingTo("50.00");
    }
}
