package com.foodie.restaurant.service.impl;

import com.foodie.shared.contract.RestaurantSummaryProvider;
import com.foodie.restaurant.repository.RestaurantRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RestaurantSummaryProviderImpl implements RestaurantSummaryProvider {

    private final RestaurantRepository restaurantRepository;

    public RestaurantSummaryProviderImpl(RestaurantRepository restaurantRepository) {
        this.restaurantRepository = restaurantRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RestaurantSummary> findByRestaurantId(UUID restaurantId) {
        return restaurantRepository.findById(restaurantId)
                .map(r -> new RestaurantSummary(
                        r.getId(),
                        r.getName(),
                        r.getStatus().name(),
                        r.getLogoImageKey()
                ));
    }
}
