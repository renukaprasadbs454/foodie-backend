package com.foodie.debug.controller;

import com.foodie.common.dto.ApiResponse;
import com.foodie.restaurant.entity.Restaurant;
import com.foodie.restaurant.repository.RestaurantRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/debug")
@Tag(name = "Debug Utilities")
public class DebugController {

    private final RestaurantRepository restaurantRepository;

    public DebugController(RestaurantRepository restaurantRepository) {
        this.restaurantRepository = restaurantRepository;
    }

    @GetMapping("/cleanup-dummy")
    @Transactional
    @Operation(summary = "Cleanup dummy restaurants")
    public ResponseEntity<ApiResponse<String>> cleanupDummy() {
        List<Restaurant> all = restaurantRepository.findAll();
        int removed = 0;
        for (Restaurant r : all) {
            if (!"Royal Hotel".equalsIgnoreCase(r.getName())) {
                r.reject("Dummy restaurant removed");
                restaurantRepository.save(r);
                removed++;
            }
        }
        return ResponseEntity.ok(ApiResponse.success("Cleaned up " + removed + " dummy restaurants."));
    }
}
