package com.example.ratelimiter.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RateLimitConfigRequest {
    private String apiKey;
    private Integer requestsPerMinute;
}
