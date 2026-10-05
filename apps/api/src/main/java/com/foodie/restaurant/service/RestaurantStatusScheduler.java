package com.foodie.restaurant.service;

import com.foodie.restaurant.entity.Restaurant;
import com.foodie.restaurant.repository.RestaurantRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RestaurantStatusScheduler {

    private static final Logger log = LoggerFactory.getLogger(RestaurantStatusScheduler.class);
    private final RestaurantRepository restaurantRepository;
    private final RestaurantCacheService restaurantCacheService;

    // Use India timezone for standard evaluation
    private static final ZoneId LOCAL_ZONE = ZoneId.of("Asia/Kolkata");

    public RestaurantStatusScheduler(RestaurantRepository restaurantRepository,
                                     RestaurantCacheService restaurantCacheService) {
        this.restaurantRepository = restaurantRepository;
        this.restaurantCacheService = restaurantCacheService;
    }

    // Run every minute
    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public void syncRestaurantStatuses() {
        LocalTime now = LocalTime.now(LOCAL_ZONE);
        String todayString = LocalDate.now(LOCAL_ZONE).getDayOfWeek().name().substring(0, 3); // MON, TUE etc.
        
        // Let's iterate all approved restaurants and sync status based on their hours
        List<Restaurant> approvedRestaurants = restaurantRepository.findAllByStatus(com.foodie.common.enums.RestaurantStatus.APPROVED);
        int changedCount = 0;

        for (Restaurant restaurant : approvedRestaurants) {
            boolean shouldBeOpen = calculateExpectedStatus(restaurant, now, todayString);
            if (restaurant.getIsOpen() != shouldBeOpen) {
                restaurant.setIsOpen(shouldBeOpen);
                restaurantRepository.save(restaurant);
                restaurantCacheService.evictRestaurant(restaurant.getId());
                changedCount++;
            }
        }

        if (changedCount > 0) {
            log.info("Synchronized operational status for {} restaurants", changedCount);
            restaurantCacheService.evictAllListCaches(); // only evict lists if something changed
        }
    }

    private boolean calculateExpectedStatus(Restaurant restaurant, LocalTime now, String todayString) {
        if (restaurant.getOpenTime() == null || restaurant.getCloseTime() == null || restaurant.getOpenDays() == null) {
            // Missing config, assume false unless manually managed but for compliance let's enforce false 
            return false;
        }

        boolean dayMatches = false;
        for (String day : restaurant.getOpenDays()) {
            if (todayString.equalsIgnoreCase(day)) {
                dayMatches = true;
                break;
            }
        }

        if (!dayMatches) {
            return false;
        }

        LocalTime open = restaurant.getOpenTime();
        LocalTime close = restaurant.getCloseTime();

        if (close.isBefore(open)) {
            // Handles overnight like 10 PM to 2 AM
            return !now.isBefore(open) || !now.isAfter(close);
        } else {
            return !now.isBefore(open) && now.isBefore(close);
        }
    }
}
