package com.foodie.payment;

import com.foodie.common.enums.OwnerType;
import com.foodie.order.entity.Order;
import com.foodie.order.repository.OrderRepository;
import com.foodie.payment.entity.OrderSettlement;
import com.foodie.payment.entity.Payment;
import com.foodie.payment.repository.OrderSettlementRepository;
import com.foodie.payment.service.SettlementService;
import com.foodie.payment.service.impl.SettlementServiceImpl;
import com.foodie.wallet.entity.LedgerEntry;
import com.foodie.wallet.entity.WalletAccount;
import com.foodie.wallet.repository.LedgerEntryRepository;
import com.foodie.wallet.repository.WalletAccountRepository;
import com.foodie.wallet.service.impl.WalletAccountHelper;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SettlementServiceTest {

    @Mock
    private OrderSettlementRepository settlementRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private WalletAccountRepository walletAccountRepository;
    @Mock
    private LedgerEntryRepository ledgerEntryRepository;
    @Mock
    private WalletAccountHelper walletAccountHelper;

    private SettlementServiceImpl settlementService;

    private UUID orderId;
    private UUID paymentId;
    private UUID restaurantId;
    private UUID deliveryPartnerId;
    private UUID customerId;
    private UUID addressId;

    @BeforeEach
    void setUp() {
        settlementService = new SettlementServiceImpl(
                settlementRepository,
                orderRepository,
                walletAccountRepository,
                ledgerEntryRepository,
                walletAccountHelper
        );

        orderId = UUID.randomUUID();
        paymentId = UUID.randomUUID();
        restaurantId = UUID.randomUUID();
        deliveryPartnerId = UUID.randomUUID();
        customerId = UUID.randomUUID();
        addressId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Scenario 1: Standard Payment Split (14% Restaurant Commission, 10% Delivery Commission, ₹40 Platform Fee)")
    void testCalculateSplit_StandardOrder() {
        BigDecimal foodSubtotal = new BigDecimal("500.00");
        BigDecimal deliveryFee = new BigDecimal("80.00");

        SettlementService.SplitBreakdownDto split = settlementService.calculateSplit(foodSubtotal, deliveryFee);

        assertEquals(new BigDecimal("620.00"), split.totalPaid());
        assertEquals(new BigDecimal("500.00"), split.foodSubtotal());
        assertEquals(new BigDecimal("80.00"), split.deliveryFee());
        assertEquals(new BigDecimal("40.00"), split.platformFee());

        assertEquals(new BigDecimal("70.00"), split.restaurantCommissionAmount()); // 14% of 500
        assertEquals(new BigDecimal("430.00"), split.restaurantPayout());           // 86% of 500

        assertEquals(new BigDecimal("8.00"), split.deliveryCommissionAmount());   // 10% of 80
        assertEquals(new BigDecimal("72.00"), split.deliveryPayout());             // 90% of 80

        assertEquals(new BigDecimal("118.00"), split.adminTotalEarnings());        // 70 + 8 + 40
    }

    @Test
    @DisplayName("Scenario 2: Zero Delivery Fee Order (Pickup/Free Delivery)")
    void testCalculateSplit_ZeroDeliveryFee() {
        BigDecimal foodSubtotal = new BigDecimal("300.00");
        BigDecimal deliveryFee = BigDecimal.ZERO;

        SettlementService.SplitBreakdownDto split = settlementService.calculateSplit(foodSubtotal, deliveryFee);

        assertEquals(new BigDecimal("340.00"), split.totalPaid());
        assertEquals(new BigDecimal("42.00"), split.restaurantCommissionAmount()); // 14% of 300
        assertEquals(new BigDecimal("258.00"), split.restaurantPayout());           // 86% of 300
        assertEquals(BigDecimal.ZERO.setScale(2), split.deliveryCommissionAmount());
        assertEquals(BigDecimal.ZERO.setScale(2), split.deliveryPayout());
        assertEquals(new BigDecimal("82.00"), split.adminTotalEarnings());         // 42 + 0 + 40
    }

    @Test
    @DisplayName("Scenario 3: Rounding Precision Test (HALF_UP rounding)")
    void testCalculateSplit_RoundingPrecision() {
        BigDecimal foodSubtotal = new BigDecimal("155.50"); // 14% = 21.77
        BigDecimal deliveryFee = new BigDecimal("45.25");  // 10% = 4.53

        SettlementService.SplitBreakdownDto split = settlementService.calculateSplit(foodSubtotal, deliveryFee);

        assertEquals(new BigDecimal("21.77"), split.restaurantCommissionAmount());
        assertEquals(new BigDecimal("133.73"), split.restaurantPayout());
        assertEquals(new BigDecimal("4.53"), split.deliveryCommissionAmount());
        assertEquals(new BigDecimal("40.72"), split.deliveryPayout());
        assertEquals(new BigDecimal("66.30"), split.adminTotalEarnings()); // 21.77 + 4.53 + 40.00
    }

    @Test
    @DisplayName("Scenario 4: Idempotency Check — Duplicate Call Returns Existing Settlement")
    void testProcessPaymentSettlement_Idempotent() {
        Order order = Order.place("ORD-1000", customerId, restaurantId, addressId, new BigDecimal("100.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("140.00"), "idemp-1");

        Payment payment = Payment.initiate(orderId, "sess-1", new BigDecimal("140.00"), BigDecimal.ZERO, "idemp-p1");

        OrderSettlement existingSettlement = OrderSettlement.create(
                orderId, paymentId, customerId, restaurantId, deliveryPartnerId,
                new BigDecimal("140.00"), new BigDecimal("100.00"), BigDecimal.ZERO, new BigDecimal("40.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("14.00"), new BigDecimal("14.00"), new BigDecimal("86.00"),
                new BigDecimal("10.00"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("54.00"), "TXN-EXISTING"
        );

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(settlementRepository.findByOrderId(any())).thenReturn(Optional.of(existingSettlement));

        OrderSettlement result = settlementService.processPaymentSettlement(payment);

        assertSame(existingSettlement, result);
        verify(settlementRepository, never()).save(any());
        verify(walletAccountRepository, never()).save(any());
    }

    @Test
    @DisplayName("Scenario 5: End-to-End Payment Settlement & Double-Entry Ledger Postings")
    void testProcessPaymentSettlement_Success() {
        Order order = Order.place("ORD-1001", customerId, restaurantId, addressId, new BigDecimal("500.00"), new BigDecimal("80.00"), BigDecimal.ZERO, new BigDecimal("25.00"), new BigDecimal("645.00"), "idemp-2");
        order.assignDeliveryPartner(deliveryPartnerId);

        Payment payment = Payment.initiate(orderId, "sess-2", new BigDecimal("645.00"), BigDecimal.ZERO, "idemp-p2");
        payment.markCaptured("CF-ORD-1001");

        when(orderRepository.findById(any())).thenReturn(Optional.of(order));
        when(settlementRepository.findByOrderId(any())).thenReturn(Optional.empty());
        when(settlementRepository.save(any(OrderSettlement.class))).thenAnswer(i -> i.getArgument(0));

        WalletAccount platformWallet = WalletAccount.open(OwnerType.PLATFORM, UUID.fromString("00000000-0000-0000-0000-000000000000"));
        WalletAccount restWallet = WalletAccount.open(OwnerType.RESTAURANT, restaurantId);
        WalletAccount driverWallet = WalletAccount.open(OwnerType.DELIVERY_PARTNER, deliveryPartnerId);

        when(walletAccountRepository.findByOwnerTypeAndOwnerId(eq(OwnerType.PLATFORM), any())).thenReturn(Optional.of(platformWallet));
        when(walletAccountRepository.findByOwnerTypeAndOwnerId(eq(OwnerType.RESTAURANT), eq(restaurantId))).thenReturn(Optional.of(restWallet));
        when(walletAccountRepository.findByOwnerTypeAndOwnerId(eq(OwnerType.DELIVERY_PARTNER), eq(deliveryPartnerId))).thenReturn(Optional.of(driverWallet));

        OrderSettlement settlement = settlementService.processPaymentSettlement(payment);

        assertNotNull(settlement);
        assertEquals(new BigDecimal("70.00"), settlement.getRestaurantCommissionAmount());
        assertEquals(new BigDecimal("430.00"), settlement.getRestaurantPayout());
        assertEquals(new BigDecimal("8.00"), settlement.getDeliveryCommissionAmount());
        assertEquals(new BigDecimal("72.00"), settlement.getDeliveryPayout());
        assertEquals(new BigDecimal("118.00"), settlement.getAdminTotalEarnings());

        // Verify wallets credited
        assertEquals(new BigDecimal("118.00"), platformWallet.getBalance());
        assertEquals(new BigDecimal("430.00"), restWallet.getBalance());
        assertEquals(new BigDecimal("72.00"), driverWallet.getBalance());

        // Verify 3 ledger entries saved (Platform, Restaurant, Delivery Partner)
        verify(ledgerEntryRepository, times(3)).save(any(LedgerEntry.class));
    }

    @Test
    @DisplayName("Scenario 6: Order Without Delivery Partner (Pickup Order)")
    void testProcessPaymentSettlement_NoDeliveryPartner() {
        Order order = Order.place("ORD-1002", customerId, restaurantId, addressId, new BigDecimal("400.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("440.00"), "idemp-3");

        Payment payment = Payment.initiate(orderId, "sess-3", new BigDecimal("440.00"), BigDecimal.ZERO, "idemp-p3");

        when(orderRepository.findById(any())).thenReturn(Optional.of(order));
        when(settlementRepository.findByOrderId(any())).thenReturn(Optional.empty());
        when(settlementRepository.save(any(OrderSettlement.class))).thenAnswer(i -> i.getArgument(0));

        WalletAccount platformWallet = WalletAccount.open(OwnerType.PLATFORM, UUID.fromString("00000000-0000-0000-0000-000000000000"));
        WalletAccount restWallet = WalletAccount.open(OwnerType.RESTAURANT, restaurantId);

        when(walletAccountRepository.findByOwnerTypeAndOwnerId(eq(OwnerType.PLATFORM), any())).thenReturn(Optional.of(platformWallet));
        when(walletAccountRepository.findByOwnerTypeAndOwnerId(eq(OwnerType.RESTAURANT), eq(restaurantId))).thenReturn(Optional.of(restWallet));

        OrderSettlement settlement = settlementService.processPaymentSettlement(payment);

        assertNotNull(settlement);
        assertNull(settlement.getDeliveryPartnerId());
        assertEquals(new BigDecimal("344.00"), settlement.getRestaurantPayout());
        assertEquals(new BigDecimal("96.00"), settlement.getAdminTotalEarnings());

        // Only 2 ledger entries created (Platform & Restaurant, no Driver)
        verify(ledgerEntryRepository, times(2)).save(any(LedgerEntry.class));
    }

    @Test
    @DisplayName("Scenario 7: Refund Adjustment Marks Settlement Status as REFUNDED")
    void testProcessRefundAdjustment() {
        OrderSettlement settlement = OrderSettlement.create(
                orderId, paymentId, customerId, restaurantId, deliveryPartnerId,
                new BigDecimal("500.00"), new BigDecimal("400.00"), new BigDecimal("60.00"),
                new BigDecimal("40.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("14.00"), new BigDecimal("56.00"), new BigDecimal("344.00"),
                new BigDecimal("10.00"), new BigDecimal("6.00"), new BigDecimal("54.00"),
                new BigDecimal("102.00"), "TXN-REFUND-1"
        );

        when(settlementRepository.findByPaymentId(paymentId)).thenReturn(Optional.of(settlement));

        settlementService.processRefundAdjustment(paymentId, new BigDecimal("100.00"), "Customer requested cancellation");

        assertEquals("REFUNDED", settlement.getSettlementStatus());
        assertEquals("REFUNDED", settlement.getPaymentStatus());
        assertEquals("REVERSED", settlement.getDistributionStatus());
        verify(settlementRepository).save(settlement);
    }

    @Test
    @DisplayName("Scenario 8: Repository Lookups (getByOrderId, getByRestaurantId)")
    void testRepositoryLookups() {
        OrderSettlement s1 = OrderSettlement.create(
                orderId, paymentId, customerId, restaurantId, deliveryPartnerId,
                new BigDecimal("500.00"), new BigDecimal("400.00"), new BigDecimal("60.00"),
                new BigDecimal("40.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("14.00"), new BigDecimal("56.00"), new BigDecimal("344.00"),
                new BigDecimal("10.00"), new BigDecimal("6.00"), new BigDecimal("54.00"),
                new BigDecimal("102.00"), "TXN-LOOKUP"
        );

        when(settlementRepository.findByOrderId(orderId)).thenReturn(Optional.of(s1));
        when(settlementRepository.findByRestaurantId(restaurantId)).thenReturn(List.of(s1));

        Optional<OrderSettlement> foundOrder = settlementService.getByOrderId(orderId);
        assertTrue(foundOrder.isPresent());
        assertEquals(orderId, foundOrder.get().getOrderId());

        List<OrderSettlement> restSettlements = settlementService.getByRestaurantId(restaurantId);
        assertEquals(1, restSettlements.size());
    }

    @Test
    @DisplayName("Scenario 9: Null Input Fallbacks in CalculateSplit")
    void testCalculateSplit_NullInputs() {
        SettlementService.SplitBreakdownDto split = settlementService.calculateSplit(null, null);

        assertEquals(new BigDecimal("40.00"), split.totalPaid());
        assertEquals(BigDecimal.ZERO, split.foodSubtotal());
        assertEquals(BigDecimal.ZERO, split.deliveryFee());
        assertEquals(new BigDecimal("40.00"), split.platformFee());
        assertEquals(new BigDecimal("40.00"), split.adminTotalEarnings());
    }

    @Test
    @DisplayName("Scenario 10: Settlement Entity Properties and Default Status")
    void testOrderSettlementEntityDefaults() {
        OrderSettlement s = OrderSettlement.create(
                orderId, paymentId, customerId, restaurantId, deliveryPartnerId,
                new BigDecimal("500.00"), new BigDecimal("400.00"), new BigDecimal("60.00"),
                new BigDecimal("40.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("14.00"), new BigDecimal("56.00"), new BigDecimal("344.00"),
                new BigDecimal("10.00"), new BigDecimal("6.00"), new BigDecimal("54.00"),
                new BigDecimal("102.00"), "TXN-12345"
        );

        assertEquals("SETTLED", s.getSettlementStatus());
        assertEquals("CAPTURED", s.getPaymentStatus());
        assertEquals("DISTRIBUTED", s.getDistributionStatus());
        assertEquals("TXN-12345", s.getTransactionReference());
        assertNotNull(s.getSettledAt());
    }
}
