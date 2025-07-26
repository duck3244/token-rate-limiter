package com.example.tokenratelimiter.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.Map;

/**
 * Rate Limiting 설정 클래스
 */
@Data
@ConfigurationProperties(prefix = "token.rate-limit")
@Component
public class TokenRateLimitConfig {
    private Map<String, ModelLimitConfig> models = new HashMap<>();
    
    @Data
    public static class ModelLimitConfig {
        private int maxTokensPerMinute = 1000;
        private int maxTokensPerHour = 10000;
        private int maxTokensPerDay = 100000;
        private int maxConcurrentRequests = 10;
    }
}