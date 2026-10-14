package com.foodie.analytics.service;

import com.foodie.analytics.dto.request.DateRangeParams;
import com.foodie.analytics.dto.response.DailySalesPointDto;
import com.foodie.analytics.dto.response.DashboardSummaryResponseDto;
import com.foodie.analytics.dto.response.OrderStatusMetricDto;
import java.util.List;

/**
 * Analytics public interface (Phase3 §2.14) — read-only reporting.
 */
public interface AnalyticsService {

    DashboardSummaryResponseDto getDashboardSummary(DateRangeParams range);

    default DashboardSummaryResponseDto getDashboardSummary(DateRangeParams range, String zoneId) {
        return getDashboardSummary(range);
    }

    List<DailySalesPointDto> getDailySales(DateRangeParams range);

    default List<DailySalesPointDto> getDailySales(DateRangeParams range, String zoneId) {
        return getDailySales(range);
    }

    List<OrderStatusMetricDto> getOrderStatusMetrics(DateRangeParams range);

    default List<OrderStatusMetricDto> getOrderStatusMetrics(DateRangeParams range, String zoneId) {
        return getOrderStatusMetrics(range);
    }
}
