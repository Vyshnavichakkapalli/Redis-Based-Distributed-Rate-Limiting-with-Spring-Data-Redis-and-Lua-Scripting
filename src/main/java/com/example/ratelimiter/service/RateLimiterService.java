package com.example.ratelimiter.service;

import com.example.ratelimiter.dto.RateLimitStatusResponse;
import com.example.ratelimiter.dto.RateLimiterResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimiterService {

    public static final String CONFIG_HASH_KEY = "rate-limit-configs";
    public static final String LOG_KEY_PREFIX = "rate_limit:log:";
    public static final int DEFAULT_LIMIT = 10;
    public static final long WINDOW_SIZE_MS = 60000; // 1 minute window

    private final RedisTemplate<String, String> redisTemplate;
    private final RedisScript<Long> rateLimiterScript;

    /**
     * Attempts to consume a rate limit token for the given API key.
     */
    public RateLimiterResult tryConsume(String apiKey) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("API key must not be empty");
        }

        int limit = getLimitForApiKey(apiKey);
        long now = System.currentTimeMillis();
        String redisKey = getLogKey(apiKey);

        Long luaResult = redisTemplate.execute(
                rateLimiterScript,
                Collections.emptyList(),
                redisKey,
                String.valueOf(now),
                String.valueOf(WINDOW_SIZE_MS),
                String.valueOf(limit)
        );

        boolean allowed = luaResult != null && luaResult == 1L;

        // Clean up outside elements to ensure accurate count
        long windowStart = now - WINDOW_SIZE_MS;
        redisTemplate.opsForZSet().removeRangeByScore(redisKey, 0, windowStart);

        Long currentCountObj = redisTemplate.opsForZSet().zCard(redisKey);
        long currentCount = (currentCountObj != null) ? currentCountObj : 0;

        long remaining = Math.max(0, limit - currentCount);
        long resetTimeSec = calculateResetTimeSec(redisKey, now);

        return RateLimiterResult.builder()
                .allowed(allowed)
                .limit(limit)
                .remaining(remaining)
                .resetTimeSec(resetTimeSec)
                .build();
    }

    /**
     * Configures custom rate limit for a specific API key in Redis Hash.
     */
    public void configureRateLimit(String apiKey, int requestsPerMinute) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("API key must not be empty");
        }
        if (requestsPerMinute <= 0) {
            throw new IllegalArgumentException("Requests per minute must be greater than 0");
        }
        redisTemplate.opsForHash().put(CONFIG_HASH_KEY, apiKey, String.valueOf(requestsPerMinute));
        log.info("Configured rate limit for apiKey '{}': {} requests/min", apiKey, requestsPerMinute);
    }

    /**
     * Gets current rate limit status for an API key.
     */
    public RateLimitStatusResponse getStatus(String apiKey) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("API key must not be empty");
        }

        int limit = getLimitForApiKey(apiKey);
        long now = System.currentTimeMillis();
        String redisKey = getLogKey(apiKey);

        long windowStart = now - WINDOW_SIZE_MS;
        redisTemplate.opsForZSet().removeRangeByScore(redisKey, 0, windowStart);

        Long currentCountObj = redisTemplate.opsForZSet().zCard(redisKey);
        long currentCount = (currentCountObj != null) ? currentCountObj : 0;

        long remaining = Math.max(0, limit - currentCount);
        long resetTimeSec = calculateResetTimeSec(redisKey, now);

        return RateLimitStatusResponse.builder()
                .apiKey(apiKey)
                .limit(limit)
                .remaining(remaining)
                .resetTime(resetTimeSec)
                .build();
    }

    /**
     * Resets the rate limit request log for a specific API key.
     */
    public void resetRateLimit(String apiKey) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("API key must not be empty");
        }
        String redisKey = getLogKey(apiKey);
        redisTemplate.delete(List.of(redisKey, redisKey + ":seq"));
        log.info("Reset rate limit log for apiKey '{}'", apiKey);
    }

    public int getLimitForApiKey(String apiKey) {
        Object valObj = redisTemplate.opsForHash().get(CONFIG_HASH_KEY, apiKey);
        if (valObj != null) {
            try {
                return Integer.parseInt(valObj.toString());
            } catch (NumberFormatException e) {
                log.warn("Invalid limit value in Redis for key '{}': {}", apiKey, valObj);
            }
        }
        return DEFAULT_LIMIT;
    }

    private String getLogKey(String apiKey) {
        return LOG_KEY_PREFIX + apiKey;
    }

    private long calculateResetTimeSec(String redisKey, long nowMs) {
        Set<TypedTuple<String>> range = redisTemplate.opsForZSet().rangeWithScores(redisKey, 0, 0);
        if (range != null && !range.isEmpty()) {
            TypedTuple<String> oldest = range.iterator().next();
            if (oldest.getScore() != null) {
                double oldestScore = oldest.getScore();
                long resetTimeMs = (long) oldestScore + WINDOW_SIZE_MS;
                return resetTimeMs / 1000;
            }
        }
        return (nowMs + WINDOW_SIZE_MS) / 1000;
    }
}
