package com.example.tokenratelimiter.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * 웹 관련 설정
 */
@Configuration
@EnableScheduling
public class WebConfig {
    
    /**
     * WebClient 빌더 설정
     */
    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder()
            .codecs(configurer -> configurer
                .defaultCodecs()
                .maxInMemorySize(10 * 1024 * 1024)) // 10MB
            .build()
            .mutate();
    }
    
    /**
     * ObjectMapper 설정
     */
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}