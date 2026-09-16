package com.foodie.payment.service;

import com.foodie.payment.entity.OrderSettlement;
import com.foodie.payment.entity.Payment;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SettlementService {

    record SplitBreakdownDto(
            BigDecimal totalPaid,
            BigDecimal foodSubtotal,
            BigDecimal deliveryFee,
            BigDecimal platformFee,
            BigDecimal restaurantCommissionRate,
            BigDecimal restaurantCommissionAmount,
            BigDecimal restaurantPayout,
            BigDecimal deliveryCommissionRate,
            BigDecimal deliveryCommissionAmount,
            BigDecimal deliveryPayout,
            BigDecimal adminTotalEarnings
    ) {}

    SplitBreakdownDto calculateSplit(BigDecimal foodSubtotal, BigDecimal deliveryFee);

    OrderSettlement processPaymentSettlement(Payment payment);

    Optional<OrderSettlement> getByOrderId(UUID orderId);

    Optional<OrderSettlement> getByPaymentId(UUID paymentId);

    List<OrderSettlement> getByRestaurantId(UUID restaurantId);

    List<OrderSettlement> getByDeliveryPartnerId(UUID deliveryPartnerId);

    Page<OrderSettlement> listSettlements(UUID restaurantId, UUID deliveryPartnerId, String status, Pageable pageable);

    void processRefundAdjustment(UUID paymentId, BigDecimal refundAmount, String reason);
}
