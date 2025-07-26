package com.example.tokenratelimiter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Token Rate Limiter 메인 애플리케이션
 */
@SpringBootApplication
public class TokenRateLimiterApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(TokenRateLimiterApplication.class, args);
    }
}