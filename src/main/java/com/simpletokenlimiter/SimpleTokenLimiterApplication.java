package com.simpletokenlimiter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Simple Token Limiter for Llama 3.2 1B Model
 * 
 * 주요 기능:
 * - 토큰 사용량 제한
 * - vLLM 프록시
 * - 사용량 모니터링
 */
@SpringBootApplication
@EnableScheduling
public class SimpleTokenLimiterApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(SimpleTokenLimiterApplication.class, args);
    }
}