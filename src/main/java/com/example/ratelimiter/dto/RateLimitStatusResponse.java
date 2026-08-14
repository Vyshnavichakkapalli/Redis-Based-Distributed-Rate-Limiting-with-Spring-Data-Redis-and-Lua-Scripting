package com.example.ratelimiter.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RateLimitStatusResponse {
    private String apiKey;
    private int limit;
    private long remaining;
    private long resetTime;
}
