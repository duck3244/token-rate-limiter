package com.example.tokenratelimiter.filter;

import com.example.tokenratelimiter.service.TokenRateLimitService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * WebFlux 컨트롤러에서 사용하는 필터
 */
@Component
@Slf4j
public class TokenRateLimitFilter implements WebFilter {
    
    private final TokenRateLimitService rateLimitService;
    private final ObjectMapper objectMapper;
    
    public TokenRateLimitFilter(TokenRateLimitService rateLimitService, ObjectMapper objectMapper) {
        this.rateLimitService = rateLimitService;
        this.objectMapper = objectMapper;
    }
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        
        // vLLM 모델 API 경로만 필터링
        if (!request.getPath().toString().startsWith("/api/v1/models/")) {
            return chain.filter(exchange);
        }
        
        String modelId = extractModelId(request.getPath().toString());
        String userId = extractUserId(request);
        
        if (modelId == null || userId == null) {
            return chain.filter(exchange);
        }
        
        // 요청 본문에서 예상 토큰 수 추출
        return extractRequestedTokens(exchange)
            .flatMap(requestedTokens -> 
                rateLimitService.checkTokenLimit(modelId, userId, requestedTokens)
                    .flatMap(allowed -> {
                        if (!allowed) {
                            return handleRateLimitExceeded(exchange);
                        }
                        
                        // 요청 허용 시 토큰 사용량 추적을 위한 데이터 저장
                        exchange.getAttributes().put("modelId", modelId);
                        exchange.getAttributes().put("userId", userId);
                        exchange.getAttributes().put("requestId", UUID.randomUUID().toString());
                        
                        return chain.filter(exchange);
                    })
            );
    }
    
    private String extractModelId(String path) {
        // /api/v1/models/{modelId}/chat/completions -> modelId 추출
        String[] parts = path.split("/");
        if (parts.length >= 5 && "models".equals(parts[3])) {
            return parts[4];
        }
        return null;
    }
    
    private String extractUserId(ServerHttpRequest request) {
        // Authorization 헤더나 API Key에서 사용자 ID 추출
        String authHeader = request.getHeaders().getFirst("Authorization");
        String apiKey = request.getHeaders().getFirst("X-API-Key");
        
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            // JWT 토큰에서 사용자 ID 추출 로직
            return extractUserIdFromToken(authHeader.substring(7));
        } else if (apiKey != null) {
            // API Key에서 사용자 ID 추출
            return extractUserIdFromApiKey(apiKey);
        }
        
        return "anonymous";
    }
    
    private Mono<Integer> extractRequestedTokens(ServerWebExchange exchange) {
        return exchange.getRequest().getBody()
            .reduce(DataBuffer::write)
            .map(dataBuffer -> {
                byte[] bytes = new byte[dataBuffer.readableByteCount()];
                dataBuffer.read(bytes);
                DataBufferUtils.release(dataBuffer);
                
                try {
                    JsonNode jsonNode = objectMapper.readTree(bytes);
                    // max_tokens 필드가 있으면 사용, 없으면 기본값
                    return jsonNode.has("max_tokens") ? 
                           jsonNode.get("max_tokens").asInt() : 100;
                } catch (Exception e) {
                    log.warn("Failed to parse request body for token estimation", e);
                    return 100; // 기본값
                }
            })
            .defaultIfEmpty(100);
    }
    
    private Mono<Void> handleRateLimitExceeded(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().add("Content-Type", "application/json");
        
        String errorResponse = """
            {
                "error": {
                    "message": "Rate limit exceeded. Please try again later.",
                    "type": "rate_limit_exceeded",
                    "code": "rate_limit_exceeded"
                }
            }
            """;
        
        DataBuffer buffer = response.bufferFactory().wrap(errorResponse.getBytes());
        return response.writeWith(Mono.just(buffer));
    }
    
    private String extractUserIdFromToken(String token) {
        // JWT 토큰 파싱 로직 구현
        try {
            // JWT 라이브러리를 사용하여 토큰에서 사용자 ID 추출
            return "user_from_jwt"; // 실제 구현 필요
        } catch (Exception e) {
            log.warn("Failed to extract user ID from token", e);
            return "anonymous";
        }
    }
    
    private String extractUserIdFromApiKey(String apiKey) {
        // API Key에서 사용자 ID 추출 로직
        // 예: API Key를 데이터베이스에서 조회하여 사용자 매핑
        return "user_from_api_key"; // 실제 구현 필요
    }
}