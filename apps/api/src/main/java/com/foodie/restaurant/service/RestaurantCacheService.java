package com.foodie.restaurant.service;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Restaurant read cache (Phase3 §6): restaurant:{id} + list geo-bucket keys, 10 min TTL, write-through evict.
 */
@Service
public class RestaurantCacheService {

    private static final Logger log = LoggerFactory.getLogger(RestaurantCacheService.class);
    private static final Duration TTL = Duration.ofMinutes(10);
    private static final String DETAIL_PREFIX = "restaurant:";
    private static final String LIST_PREFIX = "restaurants:list:";

    private final StringRedisTemplate redisTemplate;

    public RestaurantCacheService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Optional<String> getDetailJson(UUID restaurantId) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(DETAIL_PREFIX + restaurantId));
    }

    public void putDetailJson(UUID restaurantId, String json) {
        redisTemplate.opsForValue().set(DETAIL_PREFIX + restaurantId, json, TTL);
    }

    public Optional<String> getListJson(String cacheKey) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(LIST_PREFIX + cacheKey));
    }

    public void putListJson(String cacheKey, String json) {
        redisTemplate.opsForValue().set(LIST_PREFIX + cacheKey, json, TTL);
    }

    public void evictRestaurant(UUID restaurantId) {
        redisTemplate.delete(DETAIL_PREFIX + restaurantId);
        evictAllListCaches();
    }

    public void evictAllListCaches() {
        Set<String> keys = redisTemplate.keys(LIST_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
            log.debug("Evicted {} restaurant list cache keys", keys.size());
        }
    }

    public static String geoBucket(Double lat, Double lng) {
        if (lat == null || lng == null) {
            return "nogeo";
        }
        // ~5km-ish buckets at mid latitudes
        long latBucket = Math.round(lat * 20);
        long lngBucket = Math.round(lng * 20);
        return latBucket + ":" + lngBucket;
    }
}
