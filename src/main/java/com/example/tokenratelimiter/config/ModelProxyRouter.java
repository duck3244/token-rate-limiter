package com.example.tokenratelimiter.config;

import com.example.tokenratelimiter.service.ModelProxyService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RequestPredicates.*;

/**
 * 모델 프록시 라우터 설정
 */
@Configuration
public class ModelProxyRouter {
    
    @Bean
    public RouterFunction<ServerResponse> modelRoutes(ModelProxyService modelProxyService) {
        return RouterFunctions
            // 모델별 채팅 완성 API
            .route(POST("/api/v1/models/{modelId}/chat/completions"), 
                   modelProxyService::proxyToModel)
            // 사용 가능한 모델 목록 조회
            .andRoute(GET("/api/v1/models"), 
                      modelProxyService::getAvailableModels)
            // 특정 모델 상태 확인
            .andRoute(GET("/api/v1/models/{modelId}/health"), 
                      modelProxyService::checkModelHealth);
    }
}