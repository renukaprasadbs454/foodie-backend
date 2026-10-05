package com.foodie.admin;

import com.foodie.admin.dto.request.CommissionConfigDto;
import com.foodie.admin.dto.response.AdminLedgerEntryResponseDto;
import com.foodie.admin.dto.response.PaymentSplitBreakdownDto;
import com.foodie.admin.dto.response.PaymentTransactionResponseDto;
import com.foodie.admin.service.impl.AdminPaymentServiceImpl;
import com.foodie.common.enums.LedgerEntryType;
import com.foodie.common.enums.LedgerReferenceType;
import com.foodie.delivery.repository.DeliveryPartnerRepository;
import com.foodie.order.entity.Order;
import com.foodie.order.repository.OrderRepository;
import com.foodie.payment.entity.Payment;
import com.foodie.payment.repository.PaymentRepository;
import com.foodie.restaurant.repository.RestaurantRepository;
import com.foodie.wallet.entity.LedgerEntry;
import com.foodie.wallet.repository.LedgerEntryRepository;
import com.foodie.wallet.repository.WalletAccountRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminPaymentServiceImplTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private RestaurantRepository restaurantRepository;
    @Mock
    private DeliveryPartnerRepository deliveryPartnerRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private LedgerEntryRepository ledgerEntryRepository;
    @Mock
    private WalletAccountRepository walletAccountRepository;

    private AdminPaymentServiceImpl paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new AdminPaymentServiceImpl(
                orderRepository,
                restaurantRepository,
                deliveryPartnerRepository,
                paymentRepository,
                ledgerEntryRepository,
                walletAccountRepository);
    }

    @Test
    @DisplayName("Default commission rules match 15% rest, 10% delivery, and 40 fixed fee")
    void defaultCommissionRules() {
        CommissionConfigDto rules = paymentService.getCommissionRules();
        assertThat(rules.restaurantCommissionRate()).isEqualByComparingTo("15.00");
        assertThat(rules.deliveryCommissionRate()).isEqualByComparingTo("10.00");
        assertThat(rules.platformFixedFee()).isEqualByComparingTo("40.00");
    }

    @Test
    @DisplayName("Calculate payment split conserves 100% of customer paid bill")
    void calculateSplitConservesMoney() {
        BigDecimal foodSubtotal = new BigDecimal("450.00");
        BigDecimal deliveryFee = new BigDecimal("90.00");

        PaymentSplitBreakdownDto split = paymentService.calculateSplit(foodSubtotal, deliveryFee);

        assertThat(split.totalPaid()).isEqualByComparingTo("580.00");
        assertThat(split.adminFoodCommission()).isEqualByComparingTo("67.50");
        assertThat(split.adminDeliveryCommission()).isEqualByComparingTo("9.00");
        assertThat(split.platformFee()).isEqualByComparingTo("40.00");
        assertThat(split.adminTotalRevenue()).isEqualByComparingTo("116.50");
        assertThat(split.restaurantNetShare()).isEqualByComparingTo("382.50");
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

        PaymentSplitBreakdownDto split = paymentService.calculateSplit(
                new BigDecimal("500.00"),
                new BigDecimal("100.00"));

        assertThat(split.adminFoodCommission()).isEqualByComparingTo("100.00"); // 20% of 500
        assertThat(split.adminDeliveryCommission()).isEqualByComparingTo("5.00"); // 5% of 100
        assertThat(split.platformFee()).isEqualByComparingTo("50.00");
        assertThat(split.adminTotalRevenue()).isEqualByComparingTo("155.00");
        assertThat(split.restaurantNetShare()).isEqualByComparingTo("400.00");
        assertThat(split.deliveryPartnerNetShare()).isEqualByComparingTo("95.00");
        assertThat(split.totalPaid()).isEqualByComparingTo("650.00");
    }

    @Test
    @DisplayName("listTransactions maps payment and customer accurately")
    void listTransactionsSuccess() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        Payment payment = Payment.initiate(
                orderId,
                "session_123",
                new BigDecimal("250.00"),
                BigDecimal.ZERO,
                "idempotency_123");
        payment.markCaptured("cf_order_999");

        Order order = Order.place(
                "ORD-999",
                customerId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("200.00"),
                new BigDecimal("30.00"),
                BigDecimal.ZERO,
                new BigDecimal("20.00"),
                new BigDecimal("250.00"),
                "idempotency_ord_123");

        when(paymentRepository.findAll()).thenReturn(List.of(payment));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        List<PaymentTransactionResponseDto> result = paymentService.listTransactions();

        assertThat(result).hasSize(1);
        PaymentTransactionResponseDto dto = result.get(0);
        assertThat(dto.orderId()).isEqualTo(orderId);
        assertThat(dto.userId()).isEqualTo(customerId);
        assertThat(dto.amount()).isEqualByComparingTo("250.00");
        assertThat(dto.gatewayTransactionId()).isEqualTo("cf_order_999");
        assertThat(dto.gatewayName()).isEqualTo("RAZORPAY");
        assertThat(dto.status()).isEqualTo("CAPTURED");
    }

    @Test
    @DisplayName("listLedgerEntries returns mapped ledger records")
    void listLedgerEntriesSuccess() {
        UUID walletAccountId = UUID.randomUUID();
        UUID refId = UUID.randomUUID();

        LedgerEntry entry = LedgerEntry.credit(
                walletAccountId,
                new BigDecimal("150.00"),
                LedgerReferenceType.ORDER_PAYMENT,
                refId);

        when(ledgerEntryRepository.findAll()).thenReturn(List.of(entry));

        List<AdminLedgerEntryResponseDto> result = paymentService.listLedgerEntries();

        assertThat(result).hasSize(1);
        AdminLedgerEntryResponseDto dto = result.get(0);
        assertThat(dto.walletAccountId()).isEqualTo(walletAccountId);
        assertThat(dto.amount()).isEqualByComparingTo("150.00");
        assertThat(dto.entryType()).isEqualTo(LedgerEntryType.CREDIT);
        assertThat(dto.referenceType()).isEqualTo(LedgerReferenceType.ORDER_PAYMENT);
        assertThat(dto.referenceId()).isEqualTo(refId);
    }
}
