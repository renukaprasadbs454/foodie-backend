package com.foodie.admin.service.impl;

import com.foodie.admin.dto.request.CommissionConfigDto;
import com.foodie.admin.dto.response.PaymentSettlementResponseDto;
import com.foodie.admin.dto.response.PaymentSplitBreakdownDto;
import com.foodie.admin.service.AdminPaymentService;
import com.foodie.delivery.entity.DeliveryPartner;
import com.foodie.delivery.repository.DeliveryPartnerRepository;
import com.foodie.order.entity.Order;
import com.foodie.order.repository.OrderRepository;
import com.foodie.payment.entity.OrderSettlement;
import com.foodie.payment.entity.Payment;
import com.foodie.payment.repository.OrderSettlementRepository;
import com.foodie.payment.repository.PaymentRepository;
import com.foodie.payment.service.SettlementService;
import com.foodie.restaurant.entity.Restaurant;
import com.foodie.restaurant.repository.RestaurantRepository;
import com.foodie.wallet.entity.LedgerEntry;
import com.foodie.wallet.repository.LedgerEntryRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Service;

@Service
public class AdminPaymentServiceImpl implements AdminPaymentService {

    private final OrderRepository orderRepository;
    private final RestaurantRepository restaurantRepository;
    private final DeliveryPartnerRepository deliveryPartnerRepository;
    private final OrderSettlementRepository settlementRepository;
    private final PaymentRepository paymentRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final SettlementService settlementService;

    public AdminPaymentServiceImpl(
            OrderRepository orderRepository,
            RestaurantRepository restaurantRepository,
            DeliveryPartnerRepository deliveryPartnerRepository,
            OrderSettlementRepository settlementRepository,
            PaymentRepository paymentRepository,
            LedgerEntryRepository ledgerEntryRepository,
            SettlementService settlementService) {
        this.orderRepository = orderRepository;
        this.restaurantRepository = restaurantRepository;
        this.deliveryPartnerRepository = deliveryPartnerRepository;
        this.settlementRepository = settlementRepository;
        this.paymentRepository = paymentRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.settlementService = settlementService;
    }

    private final AtomicReference<CommissionConfigDto> activeConfig = new AtomicReference<>(
            new CommissionConfigDto(
                    new BigDecimal("14.00"),
                    new BigDecimal("10.00"),
                    new BigDecimal("40.00")));

    @Override
    public CommissionConfigDto getCommissionRules() {
        return activeConfig.get();
    }

    @Override
    public CommissionConfigDto updateCommissionRules(CommissionConfigDto config) {
        CommissionConfigDto updated = new CommissionConfigDto(
                config.restaurantCommissionRate().setScale(2, RoundingMode.HALF_UP),
                config.deliveryCommissionRate().setScale(2, RoundingMode.HALF_UP),
                config.platformFixedFee().setScale(2, RoundingMode.HALF_UP));
        activeConfig.set(updated);
        return updated;
    }

    @Override
    public PaymentSplitBreakdownDto calculateSplit(BigDecimal foodSubtotal, BigDecimal deliveryFee) {
        CommissionConfigDto rules = activeConfig.get();

        BigDecimal food = foodSubtotal != null ? foodSubtotal : BigDecimal.ZERO;
        BigDecimal delivery = deliveryFee != null ? deliveryFee : BigDecimal.ZERO;
        BigDecimal fee = rules.platformFixedFee();

        BigDecimal hundred = new BigDecimal("100.00");
        BigDecimal adminFoodComm = food.multiply(rules.restaurantCommissionRate())
                .divide(hundred, 2, RoundingMode.HALF_UP);

        BigDecimal adminDelivComm = delivery.multiply(rules.deliveryCommissionRate())
                .divide(hundred, 2, RoundingMode.HALF_UP);

        BigDecimal adminTotalRev = adminFoodComm.add(adminDelivComm).add(fee).setScale(2, RoundingMode.HALF_UP);
        BigDecimal restaurantShare = food.subtract(adminFoodComm).setScale(2, RoundingMode.HALF_UP);
        BigDecimal deliveryShare = delivery.subtract(adminDelivComm).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalPaid = food.add(delivery).add(fee).setScale(2, RoundingMode.HALF_UP);

        return new PaymentSplitBreakdownDto(
                totalPaid,
                food.setScale(2, RoundingMode.HALF_UP),
                delivery.setScale(2, RoundingMode.HALF_UP),
                fee.setScale(2, RoundingMode.HALF_UP),
                adminFoodComm,
                adminDelivComm,
                adminTotalRev,
                restaurantShare,
                deliveryShare);
    }

    @Override
    public List<PaymentSettlementResponseDto> listSettlements() {
        List<OrderSettlement> dbSettlements = settlementRepository.findAll();
        List<PaymentSettlementResponseDto> result = new ArrayList<>();

        if (!dbSettlements.isEmpty()) {
            for (OrderSettlement s : dbSettlements) {
                Order order = orderRepository.findById(s.getOrderId()).orElse(null);
                String orderNum = order != null ? order.getOrderNumber() : "ORD-" + s.getOrderId().toString().substring(0, 8);
                
                String restaurantName = "Partner Restaurant";
                Restaurant r = restaurantRepository.findById(s.getRestaurantId()).orElse(null);
                if (r != null) restaurantName = r.getName();

                String driverName = "Delivery Partner";
                if (s.getDeliveryPartnerId() != null) {
                    DeliveryPartner dp = deliveryPartnerRepository.findById(s.getDeliveryPartnerId()).orElse(null);
                    if (dp != null) driverName = dp.getFullName();
                }

                String customerName = "Customer " + s.getCustomerId().toString().substring(0, 4);

                result.add(new PaymentSettlementResponseDto(
                        s.getId(),
                        s.getPaymentId() != null ? s.getPaymentId() : s.getId(),
                        orderNum,
                        customerName,
                        "RAZORPAY_UPI",
                        s.getTotalPaid(),
                        s.getFoodSubtotal(),
                        s.getDeliveryFee(),
                        s.getAdminTotalEarnings(),
                        s.getRestaurantPayout(),
                        restaurantName,
                        s.getDeliveryPayout(),
                        driverName,
                        s.getDistributionStatus(),
                        s.getSettledAt()
                ));
            }
        } else {
            // Compute real settlements dynamically from existing orders if order_settlement table is currently empty
            List<Order> orders = orderRepository.findAll();
            for (Order order : orders) {
                PaymentSplitBreakdownDto split = calculateSplit(order.getSubtotal(), order.getDeliveryFee());

                String restaurantName = "Partner Restaurant";
                if (order.getRestaurantId() != null) {
                    Restaurant r = restaurantRepository.findById(order.getRestaurantId()).orElse(null);
                    if (r != null) restaurantName = r.getName();
                }

                String driverName = "Delivery Partner";
                if (order.getDeliveryPartnerId() != null) {
                    DeliveryPartner dp = deliveryPartnerRepository.findById(order.getDeliveryPartnerId()).orElse(null);
                    if (dp != null) driverName = dp.getFullName();
                }

                String customerName = "Customer " + order.getCustomerId().toString().substring(0, 4);

                result.add(new PaymentSettlementResponseDto(
                        order.getId(),
                        order.getId(),
                        order.getOrderNumber(),
                        customerName,
                        "RAZORPAY_UPI",
                        order.getTotalAmount() != null ? order.getTotalAmount() : split.totalPaid(),
                        order.getSubtotal(),
                        order.getDeliveryFee(),
                        split.adminTotalRevenue(),
                        split.restaurantNetShare(),
                        restaurantName,
                        split.deliveryPartnerNetShare(),
                        driverName,
                        "FUNDS_DISTRIBUTED",
                        order.getPlacedAt()
                ));
            }
        }

        result.sort((a, b) -> b.settledAt().compareTo(a.settledAt()));
        return result;
    }
}
