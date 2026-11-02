package com.foodie.coupon.service;

import java.time.Duration;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Cache-aside eligibility flag (Phase3 §6.1): {@code coupon:{couponId}:eligible:{customerId}}.
 * Reconstructable from PostgreSQL; never source of truth.
 */
@Component
public class CouponEligibilityCache {

    private static final String PREFIX = "coupon:";
    private static final String MID = ":eligible:";
    private static final Duration TTL = Duration.ofMinutes(5);
    private static final String ELIGIBLE = "1";

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CouponEligibilityCache.class);

    private final StringRedisTemplate redisTemplate;

    public CouponEligibilityCache(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** Positive eligibility hint after a successful DB check (cache-aside write). */
    public void markEligible(UUID couponId, UUID customerId) {
        try {
            redisTemplate.opsForValue().set(key(couponId, customerId), ELIGIBLE, TTL);
        } catch (Exception ex) {
            log.warn("Failed to write coupon eligibility to Redis cache: {}", ex.getMessage());
        }
    }

    public void invalidate(UUID couponId, UUID customerId) {
        try {
            redisTemplate.delete(key(couponId, customerId));
        } catch (Exception ex) {
            log.warn("Failed to invalidate coupon eligibility in Redis cache: {}", ex.getMessage());
        }
    }

    private static String key(UUID couponId, UUID customerId) {
        return PREFIX + couponId + MID + customerId;
    }
}
