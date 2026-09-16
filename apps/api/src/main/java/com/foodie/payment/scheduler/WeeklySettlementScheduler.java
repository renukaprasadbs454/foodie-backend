package com.foodie.payment.scheduler;

import com.foodie.order.entity.Order;
import com.foodie.order.repository.OrderRepository;
import com.foodie.restaurant.entity.Restaurant;
import com.foodie.restaurant.repository.RestaurantRepository;
import com.foodie.restaurant.service.RestaurantSettlementService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class WeeklySettlementScheduler {

    private static final Logger log = LoggerFactory.getLogger(WeeklySettlementScheduler.class);

    private final RestaurantRepository restaurantRepository;
    private final RestaurantSettlementService restaurantSettlementService;

    public WeeklySettlementScheduler(
            RestaurantRepository restaurantRepository,
            RestaurantSettlementService restaurantSettlementService) {
        this.restaurantRepository = restaurantRepository;
        this.restaurantSettlementService = restaurantSettlementService;
    }

    /**
     * Executes automatically every Monday at 01:00 AM UTC.
     * Can also be triggered on demand via Admin Payment APIs.
     */
    @Scheduled(cron = "0 0 1 * * MON")
    @Transactional
    public void executeWeeklySettlementCycle() {
        log.info("Starting automated weekly payment settlement & payout distribution batch...");
        Instant periodEnd = Instant.now();
        Instant periodStart = periodEnd.minus(7, ChronoUnit.DAYS);

        List<Restaurant> restaurants = restaurantRepository.findAll();
        int createdCount = 0;

        for (Restaurant r : restaurants) {
            try {
                var dto = restaurantSettlementService.generateSettlement(r.getId(), periodStart, periodEnd);
                if (dto != null) {
                    createdCount++;
                    log.info("Generated weekly settlement {} for restaurant {} ({})", dto.settlementNumber(), r.getName(), dto.netPayable());
                }
            } catch (Exception ex) {
                log.error("Failed to generate weekly settlement for restaurant id={}: {}", r.getId(), ex.getMessage());
            }
        }

        log.info("Completed weekly settlement cycle. Provisioned {} settlement batches for period {} to {}.",
                createdCount, periodStart, periodEnd);
    }
}
