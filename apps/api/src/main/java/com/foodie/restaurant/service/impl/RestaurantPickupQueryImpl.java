package com.foodie.restaurant.service.impl;

import com.foodie.restaurant.repository.RestaurantRepository;
import com.foodie.shared.contract.RestaurantPickupQuery;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RestaurantPickupQueryImpl implements RestaurantPickupQuery {

    private final RestaurantRepository restaurantRepository;

    public RestaurantPickupQueryImpl(RestaurantRepository restaurantRepository) {
        this.restaurantRepository = restaurantRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PickupLocation> findByRestaurantId(UUID restaurantId) {
        return restaurantRepository.findById(restaurantId).map(restaurant -> {
            var address = restaurant.getAddress();
            java.math.BigDecimal lat = address.getLatitude();
            java.math.BigDecimal lng = address.getLongitude();
            if (lat == null || lat.compareTo(java.math.BigDecimal.ZERO) == 0 || lng == null || lng.compareTo(java.math.BigDecimal.ZERO) == 0) {
                String city = address.getCity() != null ? address.getCity().toLowerCase() : "";
                if (city.contains("bengaluru") || city.contains("bangalore")) {
                    lat = java.math.BigDecimal.valueOf(12.9716);
                    lng = java.math.BigDecimal.valueOf(77.5946);
                } else {
                    lat = java.math.BigDecimal.valueOf(13.3379);
                    lng = java.math.BigDecimal.valueOf(77.1173);
                }
            }
            String zoneId = restaurant.getZoneId();
            String zoneName = restaurant.getZoneName();
            if (zoneId == null || zoneId.isBlank()) {
                String city = address.getCity() != null ? address.getCity().toLowerCase() : "";
                if (city.contains("tumkur") || city.contains("tumakuru") || (lat.doubleValue() > 13.2 && lat.doubleValue() < 13.5)) {
                    zoneId = "dz-tumkur";
                    zoneName = "Tumkur Zone";
                } else {
                    zoneId = "dz-bangalore";
                    zoneName = "Bangalore Zone";
                }
            }

            return new PickupLocation(
                    restaurant.getId(),
                    restaurant.getName(),
                    address.getLine1(),
                    address.getLine2(),
                    address.getCity(),
                    address.getPincode(),
                    lat,
                    lng,
                    zoneId,
                    zoneName
            );
        });
    }
}
