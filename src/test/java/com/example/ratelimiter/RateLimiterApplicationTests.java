package com.example.ratelimiter;

import com.example.ratelimiter.controller.RateLimiterController;
import com.example.ratelimiter.dto.RateLimitConfigRequest;
import com.example.ratelimiter.dto.RateLimitStatusResponse;
import com.example.ratelimiter.dto.RateLimiterResult;
import com.example.ratelimiter.service.RateLimiterService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RateLimiterController.class)
class RateLimiterApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RateLimiterService rateLimiterService;

    @Test
    void testProtectedEndpointAllowed() throws Exception {
        String apiKey = "key123";
        RateLimiterResult allowedResult = RateLimiterResult.builder()
                .allowed(true)
                .limit(10)
                .remaining(9)
                .resetTimeSec(1700000000L)
                .build();

        when(rateLimiterService.tryConsume(eq(apiKey))).thenReturn(allowedResult);

        mockMvc.perform(post("/api/data")
                        .header("X-API-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(header().string("X-RateLimit-Limit", "10"))
                .andExpect(header().string("X-RateLimit-Remaining", "9"))
                .andExpect(header().string("X-RateLimit-Reset", "1700000000"))
                .andExpect(jsonPath("$.message").value("Data request processed successfully"));
    }

    @Test
    void testProtectedEndpointRateLimited() throws Exception {
        String apiKey = "key123";
        RateLimiterResult limitedResult = RateLimiterResult.builder()
                .allowed(false)
                .limit(10)
                .remaining(0)
                .resetTimeSec(1700000000L)
                .build();

        when(rateLimiterService.tryConsume(eq(apiKey))).thenReturn(limitedResult);

        mockMvc.perform(post("/api/data")
                        .header("X-API-Key", apiKey))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("X-RateLimit-Limit", "10"))
                .andExpect(header().string("X-RateLimit-Remaining", "0"))
                .andExpect(header().string("X-RateLimit-Reset", "1700000000"))
                .andExpect(jsonPath("$.error").value("Too Many Requests"));
    }

    @Test
    void testConfigureRateLimit() throws Exception {
        String apiKey = "key123";
        RateLimitConfigRequest request = new RateLimitConfigRequest(apiKey, 20);

        doNothing().when(rateLimiterService).configureRateLimit(eq(apiKey), eq(20));

        mockMvc.perform(post("/api/rate-limit/configure")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Rate limit configured successfully"))
                .andExpect(jsonPath("$.apiKey").value(apiKey))
                .andExpect(jsonPath("$.requestsPerMinute").value(20));

        verify(rateLimiterService).configureRateLimit(eq(apiKey), eq(20));
    }

    @Test
    void testGetRateLimitStatus() throws Exception {
        String apiKey = "key123";
        RateLimitStatusResponse statusResponse = RateLimitStatusResponse.builder()
                .apiKey(apiKey)
                .limit(20)
                .remaining(15)
                .resetTime(1700000000L)
                .build();

        when(rateLimiterService.getStatus(eq(apiKey))).thenReturn(statusResponse);

        mockMvc.perform(get("/api/rate-limit/status/" + apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apiKey").value(apiKey))
                .andExpect(jsonPath("$.limit").value(20))
                .andExpect(jsonPath("$.remaining").value(15))
                .andExpect(jsonPath("$.resetTime").value(1700000000L));
    }

    @Test
    void testResetRateLimit() throws Exception {
        String apiKey = "key123";

        doNothing().when(rateLimiterService).resetRateLimit(eq(apiKey));

        mockMvc.perform(delete("/api/rate-limit/reset/" + apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Rate limit reset successfully for API key: " + apiKey));

        verify(rateLimiterService).resetRateLimit(eq(apiKey));
    }

    @Test
    void testMissingHeaderReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/data"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"));
    }
}
