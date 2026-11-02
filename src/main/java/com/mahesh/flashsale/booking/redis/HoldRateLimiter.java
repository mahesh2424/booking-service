package com.mahesh.flashsale.booking.redis;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class HoldRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(HoldRateLimiter.class);

    private final StringRedisTemplate redisTemplate;
    private final boolean enabled;
    private final int maxPerMinute;

    public HoldRateLimiter(
            ObjectProvider<StringRedisTemplate> redisTemplate,
            @Value("${ticketing.redis.enabled:false}") boolean enabled,
            @Value("${ticketing.rate-limit.holds-per-minute:30}") int maxPerMinute) {
        this.redisTemplate = redisTemplate.getIfAvailable();
        this.enabled = enabled && this.redisTemplate != null;
        this.maxPerMinute = maxPerMinute;
    }

    public boolean tryAcquire(String customerEmail) {
        if (!enabled) {
            return true;
        }
        String key = "rate:hold:" + customerEmail.toLowerCase();
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, Duration.ofMinutes(1));
            }
            return count == null || count <= maxPerMinute;
        } catch (RuntimeException exception) {
            log.warn("Redis unavailable for rate limiting; allowing hold request", exception);
            return true;
        }
    }
}
