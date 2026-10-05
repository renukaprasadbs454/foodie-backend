package com.foodie.admin.service.impl;

import com.foodie.admin.dto.request.CommissionConfigDto;
import com.foodie.admin.dto.response.AdminLedgerEntryResponseDto;
import com.foodie.admin.dto.response.PaymentSettlementResponseDto;
import com.foodie.admin.dto.response.PaymentSplitBreakdownDto;
import com.foodie.admin.dto.response.PaymentTransactionResponseDto;
import com.foodie.admin.service.AdminPaymentService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Service;
import com.foodie.order.repository.OrderRepository;
import com.foodie.order.entity.Order;
import com.foodie.restaurant.repository.RestaurantRepository;
import com.foodie.restaurant.entity.Restaurant;
import com.foodie.delivery.repository.DeliveryPartnerRepository;
import com.foodie.delivery.entity.DeliveryPartner;
import com.foodie.payment.repository.PaymentRepository;
import com.foodie.payment.entity.Payment;
import com.foodie.wallet.repository.LedgerEntryRepository;
import com.foodie.wallet.entity.LedgerEntry;
import com.foodie.wallet.repository.WalletAccountRepository;
import com.foodie.wallet.entity.WalletAccount;

@Service
public class AdminPaymentServiceImpl implements AdminPaymentService {

        private final OrderRepository orderRepository;
        private final RestaurantRepository restaurantRepository;
        private final DeliveryPartnerRepository deliveryPartnerRepository;
        private final PaymentRepository paymentRepository;
        private final LedgerEntryRepository ledgerEntryRepository;
        private final WalletAccountRepository walletAccountRepository;

        public AdminPaymentServiceImpl(
                        OrderRepository orderRepository,
                        RestaurantRepository restaurantRepository,
                        DeliveryPartnerRepository deliveryPartnerRepository,
                        PaymentRepository paymentRepository,
                        LedgerEntryRepository ledgerEntryRepository,
                        WalletAccountRepository walletAccountRepository) {
                this.orderRepository = orderRepository;
                this.restaurantRepository = restaurantRepository;
                this.deliveryPartnerRepository = deliveryPartnerRepository;
                this.paymentRepository = paymentRepository;
                this.ledgerEntryRepository = ledgerEntryRepository;
                this.walletAccountRepository = walletAccountRepository;
        }

        private final AtomicReference<CommissionConfigDto> activeConfig = new AtomicReference<>(
                        new CommissionConfigDto(
                                        new BigDecimal("15.00"),
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
                List<Order> orders = orderRepository.findAll();
                List<PaymentSettlementResponseDto> result = new ArrayList<>();

                for (Order order : orders) {
                        PaymentSplitBreakdownDto split = calculateSplit(order.getSubtotal(), order.getDeliveryFee());

                        String restaurantName = "Unknown Restaurant";
                        if (order.getRestaurantId() != null) {
                                Restaurant r = restaurantRepository.findById(order.getRestaurantId()).orElse(null);
                                if (r != null)
                                        restaurantName = r.getName();
                        }

                        String driverName = "No Delivery Partner";
                        if (order.getDeliveryPartnerId() != null) {
                                DeliveryPartner dp = deliveryPartnerRepository.findById(order.getDeliveryPartnerId())
                                                .orElse(null);
                                if (dp != null)
                                        driverName = dp.getFullName();
                        }

                        String customerName = "Customer";
                        if (order.getCustomerId() != null) {
                                customerName = "Customer " + order.getCustomerId().toString().substring(0, 4);
                        }

                        result.add(new PaymentSettlementResponseDto(
                                        order.getId(),
                                        order.getId(),
                                        order.getOrderNumber(),
                                        customerName,
                                        "RAZORPAY",
                                        split.totalPaid(),
                                        split.foodSubtotal(),
                                        split.deliveryFee(),
                                        split.adminTotalRevenue(),
                                        split.restaurantNetShare(),
                                        restaurantName,
                                        split.deliveryPartnerNetShare(),
                                        driverName,
                                        "FUNDS_DISTRIBUTED",
                                        order.getPlacedAt()));
                }

                // Sort descending by placedAt
                result.sort((a, b) -> {
                        if (a.settledAt() == null && b.settledAt() == null) return 0;
                        if (a.settledAt() == null) return 1;
                        if (b.settledAt() == null) return -1;
                        return b.settledAt().compareTo(a.settledAt());
                });

                return result;
        }

        @Override
        public List<PaymentTransactionResponseDto> listTransactions() {
                List<Payment> payments = paymentRepository.findAll();
                List<PaymentTransactionResponseDto> result = new ArrayList<>();

                for (Payment payment : payments) {
                        UUID userId = null;
                        if (payment.getOrderId() != null) {
                                Order order = orderRepository.findById(payment.getOrderId()).orElse(null);
                                if (order != null) {
                                        userId = order.getCustomerId();
                                }
                        }

                        String paymentMethod = "RAZORPAY_UPI";
                        if (payment.getWalletAmount() != null && payment.getWalletAmount().compareTo(BigDecimal.ZERO) > 0) {
                                if (payment.getAmount() == null || payment.getAmount().compareTo(BigDecimal.ZERO) == 0) {
                                        paymentMethod = "FOODIE_WALLET";
                                } else {
                                        paymentMethod = "SPLIT_WALLET_GATEWAY";
                                }
                        }

                        String gatewayTxId = payment.getCashfreeOrderId() != null
                                        ? payment.getCashfreeOrderId()
                                        : (payment.getPaymentSessionId() != null
                                                        ? payment.getPaymentSessionId()
                                                        : (payment.getId() != null ? payment.getId().toString() : "N/A"));

                        String gatewayName = payment.getCashfreeOrderId() != null && payment.getCashfreeOrderId().startsWith("WALLET_")
                                        ? "FOODIE_WALLET"
                                        : "RAZORPAY";

                        result.add(new PaymentTransactionResponseDto(
                                        payment.getId(),
                                        payment.getOrderId(),
                                        userId,
                                        payment.getAmount() != null ? payment.getAmount() : BigDecimal.ZERO,
                                        "INR",
                                        paymentMethod,
                                        payment.getStatus() != null ? payment.getStatus().name() : "CAPTURED",
                                        gatewayTxId,
                                        gatewayName,
                                        payment.getCreatedAt(),
                                        payment.getUpdatedAt()));
                }

                result.sort((a, b) -> {
                        if (a.createdAt() == null && b.createdAt() == null) return 0;
                        if (a.createdAt() == null) return 1;
                        if (b.createdAt() == null) return -1;
                        return b.createdAt().compareTo(a.createdAt());
                });

                return result;
        }

        @Override
        public List<AdminLedgerEntryResponseDto> listLedgerEntries() {
                List<LedgerEntry> entries = ledgerEntryRepository.findAll();
                List<AdminLedgerEntryResponseDto> result = new ArrayList<>();

                for (LedgerEntry entry : entries) {
                        BigDecimal balanceAfter = BigDecimal.ZERO;
                        if (entry.getWalletAccountId() != null) {
                                WalletAccount account = walletAccountRepository.findById(entry.getWalletAccountId()).orElse(null);
                                if (account != null && account.getBalance() != null) {
                                        balanceAfter = account.getBalance();
                                }
                        }

                        result.add(new AdminLedgerEntryResponseDto(
                                        entry.getId(),
                                        entry.getWalletAccountId(),
                                        entry.getAmount() != null ? entry.getAmount() : BigDecimal.ZERO,
                                        entry.getEntryType(),
                                        entry.getReferenceType(),
                                        entry.getReferenceId(),
                                        balanceAfter,
                                        entry.getCreatedAt()));
                }

                result.sort((a, b) -> {
                        if (a.createdAt() == null && b.createdAt() == null) return 0;
                        if (a.createdAt() == null) return 1;
                        if (b.createdAt() == null) return -1;
                        return b.createdAt().compareTo(a.createdAt());
                });

                return result;
        }
}
