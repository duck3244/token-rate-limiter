package com.simpletokenlimiter.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * 웹 설정
 */
@Configuration
public class WebConfig {
    
    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder()
            .codecs(configurer -> configurer
                .defaultCodecs()
                .maxInMemorySize(5 * 1024 * 1024)); // 5MB
    }
    
    @Bean
    public WebClient webClient(WebClient.Builder builder) {
        return builder.build();
    }
}