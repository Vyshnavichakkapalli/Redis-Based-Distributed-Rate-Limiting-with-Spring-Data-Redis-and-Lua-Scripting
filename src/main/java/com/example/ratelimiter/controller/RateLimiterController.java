package com.example.ratelimiter.controller;

import com.example.ratelimiter.dto.RateLimitConfigRequest;
import com.example.ratelimiter.dto.RateLimitStatusResponse;
import com.example.ratelimiter.dto.RateLimiterResult;
import com.example.ratelimiter.service.RateLimiterService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class RateLimiterController {

    private final RateLimiterService rateLimiterService;

    /**
     * POST /api/data: Protected endpoint enforcing sliding window log rate limiting.
     */
    @PostMapping("/data")
    public ResponseEntity<?> processData(
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            HttpServletResponse response) {

        if (apiKey == null || apiKey.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Bad Request", "message", "Missing or empty X-API-Key header"));
        }

        RateLimiterResult result = rateLimiterService.tryConsume(apiKey);

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-RateLimit-Limit", String.valueOf(result.getLimit()));
        headers.set("X-RateLimit-Remaining", String.valueOf(result.getRemaining()));
        headers.set("X-RateLimit-Reset", String.valueOf(result.getResetTimeSec()));

        if (result.isAllowed()) {
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(Map.of(
                            "message", "Data request processed successfully",
                            "data", "Sample REST API protected data payload"
                    ));
        } else {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .headers(headers)
                    .body(Map.of(
                            "error", "Too Many Requests",
                            "message", "Rate limit exceeded for API key: " + apiKey
                    ));
        }
    }

    /**
     * POST /api/rate-limit/configure: Configures custom rate limits for API keys.
     */
    @PostMapping("/rate-limit/configure")
    public ResponseEntity<?> configureRateLimit(@RequestBody RateLimitConfigRequest request) {
        if (request.getApiKey() == null || request.getApiKey().trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Bad Request", "message", "apiKey must not be empty"));
        }
        if (request.getRequestsPerMinute() == null || request.getRequestsPerMinute() <= 0) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Bad Request", "message", "requestsPerMinute must be greater than 0"));
        }

        rateLimiterService.configureRateLimit(request.getApiKey(), request.getRequestsPerMinute());

        return ResponseEntity.ok(Map.of(
                "message", "Rate limit configured successfully",
                "apiKey", request.getApiKey(),
                "requestsPerMinute", request.getRequestsPerMinute()
        ));
    }

    /**
     * GET /api/rate-limit/status/{apiKey}: Queries current rate limit status for an API key.
     */
    @GetMapping("/rate-limit/status/{apiKey}")
    public ResponseEntity<RateLimitStatusResponse> getRateLimitStatus(@PathVariable("apiKey") String apiKey) {
        RateLimitStatusResponse status = rateLimiterService.getStatus(apiKey);
        return ResponseEntity.ok(status);
    }

    /**
     * DELETE /api/rate-limit/reset/{apiKey}: Clears request log for a specific API key.
     */
    @DeleteMapping("/rate-limit/reset/{apiKey}")
    public ResponseEntity<?> resetRateLimit(@PathVariable("apiKey") String apiKey) {
        rateLimiterService.resetRateLimit(apiKey);
        return ResponseEntity.ok(Map.of(
                "message", "Rate limit reset successfully for API key: " + apiKey
        ));
    }
}
