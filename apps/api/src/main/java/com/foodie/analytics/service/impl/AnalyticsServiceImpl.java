package com.foodie.analytics.service.impl;

import com.foodie.analytics.dto.request.DateRangeParams;
import com.foodie.analytics.dto.response.DailySalesPointDto;
import com.foodie.analytics.dto.response.DashboardSummaryResponseDto;
import com.foodie.analytics.dto.response.OrderStatusMetricDto;
import com.foodie.analytics.service.AnalyticsService;
import com.foodie.common.exception.BadRequestException;
import com.foodie.common.exception.ErrorCode;
import com.foodie.shared.contract.CustomerAnalyticsQuery;
import com.foodie.shared.contract.DeliveryAnalyticsQuery;
import com.foodie.shared.contract.OrderAnalyticsQuery;
import com.foodie.shared.contract.PaymentAnalyticsQuery;
import com.foodie.shared.contract.RestaurantAnalyticsQuery;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalyticsServiceImpl implements AnalyticsService {

    private final OrderAnalyticsQuery orderAnalyticsQuery;
    private final PaymentAnalyticsQuery paymentAnalyticsQuery;
    private final RestaurantAnalyticsQuery restaurantAnalyticsQuery;
    private final DeliveryAnalyticsQuery deliveryAnalyticsQuery;
    private final CustomerAnalyticsQuery customerAnalyticsQuery;

    public AnalyticsServiceImpl(
            OrderAnalyticsQuery orderAnalyticsQuery,
            PaymentAnalyticsQuery paymentAnalyticsQuery,
            RestaurantAnalyticsQuery restaurantAnalyticsQuery,
            DeliveryAnalyticsQuery deliveryAnalyticsQuery,
            CustomerAnalyticsQuery customerAnalyticsQuery
    ) {
        this.orderAnalyticsQuery = orderAnalyticsQuery;
        this.paymentAnalyticsQuery = paymentAnalyticsQuery;
        this.restaurantAnalyticsQuery = restaurantAnalyticsQuery;
        this.deliveryAnalyticsQuery = deliveryAnalyticsQuery;
        this.customerAnalyticsQuery = customerAnalyticsQuery;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.foodie.admin.service.AdminZoneService adminZoneService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.foodie.restaurant.repository.RestaurantRepository restaurantRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.foodie.delivery.repository.DeliveryPartnerRepository deliveryPartnerRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.foodie.user.repository.CustomerRepository customerRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.foodie.user.repository.AddressRepository addressRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.foodie.order.repository.OrderRepository orderRepository;

    @Override
    @Transactional(readOnly = true)
    public DashboardSummaryResponseDto getDashboardSummary(DateRangeParams range) {
        validate(range);
        var from = range.fromInclusive();
        var to = range.toExclusive();

        long totalOrders = orderAnalyticsQuery.countPlacedBetween(from, to);
        BigDecimal totalRevenue = paymentAnalyticsQuery.sumCapturedBetween(from, to);
        long activeRestaurants = restaurantAnalyticsQuery.countApproved();
        long activeDeliveryPartners = deliveryAnalyticsQuery.countKycVerified();
        long newCustomers = customerAnalyticsQuery.countCreatedBetween(from, to);
        BigDecimal avgOrderValue = totalOrders == 0
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : totalRevenue.divide(BigDecimal.valueOf(totalOrders), 2, RoundingMode.HALF_UP);

        return new DashboardSummaryResponseDto(
                totalOrders,
                totalRevenue,
                activeRestaurants,
                activeDeliveryPartners,
                newCustomers,
                avgOrderValue
        );
    }

    @Override
    @Transactional(readOnly = true)
    public DashboardSummaryResponseDto getDashboardSummary(DateRangeParams range, String zoneId) {
        if (zoneId == null || zoneId.isBlank() || zoneId.equalsIgnoreCase("ALL") || adminZoneService == null) {
            return getDashboardSummary(range);
        }
        validate(range);
        var from = range.fromInclusive();
        var to = range.toExclusive();
        com.foodie.admin.entity.LocationZone zone = adminZoneService.validateZone(zoneId);

        long activeRestaurants = 0;
        if (restaurantRepository != null) {
            activeRestaurants = restaurantRepository.findAll().stream()
                    .filter(r -> r.getStatus() == com.foodie.common.enums.RestaurantStatus.APPROVED)
                    .filter(r -> adminZoneService.isLocationInZone(
                            r.getLatitude() != null ? r.getLatitude().doubleValue() : null,
                            r.getLongitude() != null ? r.getLongitude().doubleValue() : null,
                            r.getAddress() != null ? r.getAddress().getCity() : null,
                            zone))
                    .count();
        }

        long activeDeliveryPartners = 0;
        if (deliveryPartnerRepository != null) {
            activeDeliveryPartners = deliveryPartnerRepository.findAll().stream()
                    .filter(p -> p.getKycStatus() == com.foodie.common.enums.KycStatus.VERIFIED)
                    .filter(p -> {
                        if (p.getZoneId() != null && p.getZoneId().equalsIgnoreCase(zone.getId())) return true;
                        if (p.getZoneName() != null && p.getZoneName().equalsIgnoreCase(zone.getZoneName())) return true;
                        return adminZoneService.isLocationInZone(p.getLatitude(), p.getLongitude(), p.getCity(), zone);
                    })
                    .count();
        }

        long newCustomers = 0;
        if (customerRepository != null && addressRepository != null) {
            newCustomers = customerRepository.findAll().stream()
                    .filter(c -> c.getCreatedAt() != null && !c.getCreatedAt().isBefore(from) && c.getCreatedAt().isBefore(to))
                    .filter(c -> {
                        var addrs = addressRepository.findByCustomerIdOrderByCreatedAtAsc(c.getId());
                        for (var addr : addrs) {
                            Double lat = addr.getLatitude() != null ? addr.getLatitude().doubleValue() : null;
                            Double lng = addr.getLongitude() != null ? addr.getLongitude().doubleValue() : null;
                            if (adminZoneService.isLocationInZone(lat, lng, addr.getCity(), zone)) {
                                return true;
                            }
                        }
                        return false;
                    })
                    .count();
        }

        long totalOrders = 0;
        BigDecimal totalRevenue = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        if (orderRepository != null && restaurantRepository != null) {
            var orders = orderRepository.findAll().stream()
                    .filter(o -> o.getPlacedAt() != null && !o.getPlacedAt().isBefore(from) && o.getPlacedAt().isBefore(to))
                    .filter(o -> {
                        if (o.getRestaurantId() == null) return false;
                        var r = restaurantRepository.findById(o.getRestaurantId()).orElse(null);
                        if (r == null) return false;
                        return adminZoneService.isLocationInZone(
                                r.getLatitude() != null ? r.getLatitude().doubleValue() : null,
                                r.getLongitude() != null ? r.getLongitude().doubleValue() : null,
                                r.getAddress() != null ? r.getAddress().getCity() : null,
                                zone);
                    })
                    .toList();

            totalOrders = orders.size();
            for (var o : orders) {
                if (o.getTotalAmount() != null) {
                    totalRevenue = totalRevenue.add(o.getTotalAmount());
                }
            }
        }

        BigDecimal avgOrderValue = totalOrders == 0
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : totalRevenue.divide(BigDecimal.valueOf(totalOrders), 2, RoundingMode.HALF_UP);

        return new DashboardSummaryResponseDto(
                totalOrders,
                totalRevenue,
                activeRestaurants,
                activeDeliveryPartners,
                newCustomers,
                avgOrderValue
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<DailySalesPointDto> getDailySales(DateRangeParams range) {
        validate(range);
        return orderAnalyticsQuery.dailySalesByPlacedAt(range.fromInclusive(), range.toExclusive())
                .stream()
                .map(row -> new DailySalesPointDto(row.date(), row.orderCount(), row.revenue()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DailySalesPointDto> getDailySales(DateRangeParams range, String zoneId) {
        if (zoneId == null || zoneId.isBlank() || zoneId.equalsIgnoreCase("ALL") || adminZoneService == null || orderRepository == null || restaurantRepository == null) {
            return getDailySales(range);
        }
        validate(range);
        var from = range.fromInclusive();
        var to = range.toExclusive();
        com.foodie.admin.entity.LocationZone zone = adminZoneService.validateZone(zoneId);

        var zoneOrders = orderRepository.findAll().stream()
                .filter(o -> o.getPlacedAt() != null && !o.getPlacedAt().isBefore(from) && o.getPlacedAt().isBefore(to))
                .filter(o -> {
                    if (o.getRestaurantId() == null) return false;
                    var r = restaurantRepository.findById(o.getRestaurantId()).orElse(null);
                    if (r == null) return false;
                    return adminZoneService.isLocationInZone(
                            r.getLatitude() != null ? r.getLatitude().doubleValue() : null,
                            r.getLongitude() != null ? r.getLongitude().doubleValue() : null,
                            r.getAddress() != null ? r.getAddress().getCity() : null,
                            zone);
                })
                .toList();

        java.util.Map<java.time.LocalDate, List<com.foodie.order.entity.Order>> byDate = zoneOrders.stream()
                .collect(java.util.stream.Collectors.groupingBy(o -> o.getPlacedAt().atZone(java.time.ZoneOffset.UTC).toLocalDate()));

        return byDate.entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey())
                .map(entry -> {
                    long count = entry.getValue().size();
                    BigDecimal rev = entry.getValue().stream()
                            .map(o -> o.getTotalAmount() != null ? o.getTotalAmount() : BigDecimal.ZERO)
                            .reduce(BigDecimal.ZERO, BigDecimal::add)
                            .setScale(2, RoundingMode.HALF_UP);
                    return new DailySalesPointDto(entry.getKey(), count, rev);
                })
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderStatusMetricDto> getOrderStatusMetrics(DateRangeParams range) {
        validate(range);
        List<OrderAnalyticsQuery.StatusCountRow> rows =
                orderAnalyticsQuery.countByStatusPlacedBetween(range.fromInclusive(), range.toExclusive());
        long total = rows.stream().mapToLong(OrderAnalyticsQuery.StatusCountRow::count).sum();
        List<OrderStatusMetricDto> metrics = new ArrayList<>();
        for (OrderAnalyticsQuery.StatusCountRow row : rows) {
            BigDecimal pct = total == 0
                    ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.valueOf(row.count())
                            .multiply(BigDecimal.valueOf(100))
                            .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
            metrics.add(new OrderStatusMetricDto(row.status(), row.count(), pct));
        }
        metrics.sort(Comparator.comparing(OrderStatusMetricDto::status));
        return metrics;
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderStatusMetricDto> getOrderStatusMetrics(DateRangeParams range, String zoneId) {
        if (zoneId == null || zoneId.isBlank() || zoneId.equalsIgnoreCase("ALL") || adminZoneService == null || orderRepository == null || restaurantRepository == null) {
            return getOrderStatusMetrics(range);
        }
        validate(range);
        var from = range.fromInclusive();
        var to = range.toExclusive();
        com.foodie.admin.entity.LocationZone zone = adminZoneService.validateZone(zoneId);

        var zoneOrders = orderRepository.findAll().stream()
                .filter(o -> o.getPlacedAt() != null && !o.getPlacedAt().isBefore(from) && o.getPlacedAt().isBefore(to))
                .filter(o -> {
                    if (o.getRestaurantId() == null) return false;
                    var r = restaurantRepository.findById(o.getRestaurantId()).orElse(null);
                    if (r == null) return false;
                    return adminZoneService.isLocationInZone(
                            r.getLatitude() != null ? r.getLatitude().doubleValue() : null,
                            r.getLongitude() != null ? r.getLongitude().doubleValue() : null,
                            r.getAddress() != null ? r.getAddress().getCity() : null,
                            zone);
                })
                .toList();

        long total = zoneOrders.size();
        java.util.Map<String, Long> byStatus = zoneOrders.stream()
                .collect(java.util.stream.Collectors.groupingBy(o -> o.getStatus() != null ? o.getStatus().name() : "PENDING", java.util.stream.Collectors.counting()));

        List<OrderStatusMetricDto> metrics = new ArrayList<>();
        for (var entry : byStatus.entrySet()) {
            BigDecimal pct = total == 0
                    ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.valueOf(entry.getValue())
                            .multiply(BigDecimal.valueOf(100))
                            .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
            metrics.add(new OrderStatusMetricDto(entry.getKey(), entry.getValue(), pct));
        }
        metrics.sort(Comparator.comparing(OrderStatusMetricDto::status));
        return metrics;
    }

    private static void validate(DateRangeParams range) {
        if (range == null || range.dateFrom() == null || range.dateTo() == null) {
            throw new BadRequestException(
                    ErrorCode.VALIDATION_FAILED, "dateFrom and dateTo are required ISO dates.");
        }
        if (range.dateFrom().isAfter(range.dateTo())) {
            throw new BadRequestException(
                    ErrorCode.VALIDATION_FAILED, "dateFrom must be on or before dateTo.");
        }
    }
}
