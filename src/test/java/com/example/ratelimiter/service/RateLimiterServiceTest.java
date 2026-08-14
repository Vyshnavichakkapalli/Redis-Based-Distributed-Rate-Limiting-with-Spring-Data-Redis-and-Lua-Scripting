package com.example.ratelimiter.service;

import com.example.ratelimiter.dto.RateLimitStatusResponse;
import com.example.ratelimiter.dto.RateLimiterResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.RedisScript;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimiterServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private RedisScript<Long> rateLimiterScript;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    @InjectMocks
    private RateLimiterService rateLimiterService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        lenient().when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
    }

    @Test
    void testTryConsumeAllowed() {
        String apiKey = "test-key";
        when(hashOperations.get(RateLimiterService.CONFIG_HASH_KEY, apiKey)).thenReturn("5");
        when(redisTemplate.execute(eq(rateLimiterScript), anyList(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(1L);
        when(zSetOperations.zCard(anyString())).thenReturn(1L);

        RateLimiterResult result = rateLimiterService.tryConsume(apiKey);

        assertTrue(result.isAllowed());
        assertEquals(5, result.getLimit());
        assertEquals(4, result.getRemaining());
    }

    @Test
    void testTryConsumeDenied() {
        String apiKey = "test-key";
        when(hashOperations.get(RateLimiterService.CONFIG_HASH_KEY, apiKey)).thenReturn("5");
        when(redisTemplate.execute(eq(rateLimiterScript), anyList(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(0L);
        when(zSetOperations.zCard(anyString())).thenReturn(5L);

        RateLimiterResult result = rateLimiterService.tryConsume(apiKey);

        assertFalse(result.isAllowed());
        assertEquals(5, result.getLimit());
        assertEquals(0, result.getRemaining());
    }

    @Test
    void testConfigureRateLimit() {
        String apiKey = "new-key";
        rateLimiterService.configureRateLimit(apiKey, 15);
        verify(hashOperations).put(RateLimiterService.CONFIG_HASH_KEY, apiKey, "15");
    }

    @Test
    void testGetStatusDefaultLimit() {
        String apiKey = "unconfigured-key";
        when(hashOperations.get(RateLimiterService.CONFIG_HASH_KEY, apiKey)).thenReturn(null);
        when(zSetOperations.zCard(anyString())).thenReturn(2L);

        RateLimitStatusResponse status = rateLimiterService.getStatus(apiKey);

        assertEquals(10, status.getLimit());
        assertEquals(8, status.getRemaining());
    }

    @Test
    void testResetRateLimit() {
        String apiKey = "key-to-reset";
        rateLimiterService.resetRateLimit(apiKey);
        verify(redisTemplate).delete(anyList());
    }
}
