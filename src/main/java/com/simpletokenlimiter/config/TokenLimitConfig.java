package com.simpletokenlimiter.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 토큰 제한 설정
 */
@Data
@ConfigurationProperties(prefix = "token.limit")
@Component
public class TokenLimitConfig {
    
    private int maxTokensPerMinute = 1000;
    private int maxTokensPerHour = 10000;
    private int maxTokensPerDay = 100000;
    private int maxConcurrentRequests = 5;
    
    // Llama 3.2 1B 모델 설정
    private String modelName = "llama3.2-1b";
    private String vllmUrl = "http://localhost:8000";
}