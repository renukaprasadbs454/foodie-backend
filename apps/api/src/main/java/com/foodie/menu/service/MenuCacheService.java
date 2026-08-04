package com.foodie.menu.service;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Menu read cache: menu:{restaurantId}, 10 min, write-through evict (Phase3 §6).
 */
@Service
public class MenuCacheService {

    private static final Duration TTL = Duration.ofMinutes(10);
    private static final String PREFIX = "menu:";

    private final StringRedisTemplate redisTemplate;

    public MenuCacheService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Optional<String> get(UUID restaurantId) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(PREFIX + restaurantId));
    }

    public void put(UUID restaurantId, String json) {
        redisTemplate.opsForValue().set(PREFIX + restaurantId, json, TTL);
    }

    public void evict(UUID restaurantId) {
        redisTemplate.delete(PREFIX + restaurantId);
    }
}
