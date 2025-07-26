package com.example.tokenratelimiter.service;

import com.example.tokenratelimiter.exception.ModelNotFoundException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * vLLM 모델 프록시 서비스
 */
@Service
@Slf4j
public class ModelProxyService {
    
    private final WebClient webClient;
    private final TokenRateLimitService rateLimitService;
    private final ModelRegistryService modelRegistryService;
    private final ObjectMapper objectMapper;
    
    public ModelProxyService(WebClient.Builder webClientBuilder, 
                           TokenRateLimitService rateLimitService,
                           ModelRegistryService modelRegistryService,
                           ObjectMapper objectMapper) {
        this.webClient = webClientBuilder.build();
        this.rateLimitService = rateLimitService;
        this.modelRegistryService = modelRegistryService;
        this.objectMapper = objectMapper;
    }
    
    /**
     * 모델에게 요청을 프록시하고 토큰 사용량을 추적
     */
    public Mono<ServerResponse> proxyToModel(ServerRequest request) {
        String modelId = request.pathVariable("modelId");
        String userId = extractUserId(request);
        String requestId = UUID.randomUUID().toString();
        
        return modelRegistryService.getModelEndpoint(modelId)
            .switchIfEmpty(Mono.error(new ModelNotFoundException(modelId)))
            .flatMap(endpoint -> 
                request.bodyToMono(String.class)
                    .flatMap(body -> {
                        int estimatedTokens = estimateTokensFromRequest(body);
                        
                        return rateLimitService.checkTokenLimit(modelId, userId, estimatedTokens)
                            .flatMap(allowed -> {
                                if (!allowed) {
                                    return ServerResponse.status(HttpStatus.TOO_MANY_REQUESTS)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .bodyValue(createRateLimitResponse());
                                }
                                
                                return forwardRequestToModel(endpoint, body, request, modelId, userId, requestId);
                            });
                    })
            );
    }
    
    /**
     * 사용 가능한 모델 목록 조회
     */
    public Mono<ServerResponse> getAvailableModels(ServerRequest request) {
        return modelRegistryService.getAvailableModels()
            .flatMap(models -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("data", models)));
    }
    
    /**
     * 모델 상태 확인
     */
    public Mono<ServerResponse> checkModelHealth(ServerRequest request) {
        String modelId = request.pathVariable("modelId");
        
        return modelRegistryService.isModelHealthy(modelId)
            .flatMap(healthy -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                    "model", modelId,
                    "healthy", healthy,
                    "timestamp", System.currentTimeMillis()
                )));
    }
    
    private Mono<ServerResponse> forwardRequestToModel(String endpoint, String body, 
                                                     ServerRequest request, String modelId, 
                                                     String userId, String requestId) {
        return webClient.post()
            .uri(endpoint + "/v1/chat/completions")
            .contentType(MediaType.APPLICATION_JSON)
            .headers(headers -> copyHeaders(request, headers))
            .bodyValue(body)
            .retrieve()
            .bodyToMono(String.class)
            .flatMap(response -> {
                // 실제 토큰 사용량 추출 및 기록
                int actualTokens = extractTokenUsageFromResponse(response);
                
                return rateLimitService.recordTokenUsage(modelId, userId, actualTokens, requestId)
                    .then(ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(response));
            })
            .onErrorResume(WebClientResponseException.class, ex -> {
                log.error("Error forwarding request to model {}: {}", modelId, ex.getMessage());
                return ServerResponse.status(ex.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(createErrorResponse(ex.getMessage()));
            });
    }
    
    private void copyHeaders(ServerRequest request, HttpHeaders headers) {
        request.headers().asHttpHeaders().forEach((key, values) -> {
            if (!key.equalsIgnoreCase("host") && 
                !key.equalsIgnoreCase("content-length")) {
                headers.addAll(key, values);
            }
        });
    }
    
    private int estimateTokensFromRequest(String body) {
        try {
            JsonNode jsonNode = objectMapper.readTree(body);
            
            // max_tokens 필드가 있으면 사용
            if (jsonNode.has("max_tokens")) {
                return jsonNode.get("max_tokens").asInt();
            }
            
            // 메시지 내용을 기반으로 토큰 추정 (간단한 휴리스틱)
            if (jsonNode.has("messages")) {
                int totalChars = 0;
                JsonNode messages = jsonNode.get("messages");
                for (JsonNode message : messages) {
                    if (message.has("content")) {
                        totalChars += message.get("content").asText().length();
                    }
                }
                // 대략적으로 4글자당 1토큰으로 추정
                return Math.max(totalChars / 4, 50);
            }
            
            return 100; // 기본값
        } catch (Exception e) {
            log.warn("Failed to estimate tokens from request", e);
            return 100;
        }
    }
    
    private int extractTokenUsageFromResponse(String response) {
        try {
            JsonNode jsonNode = objectMapper.readTree(response);
            
            if (jsonNode.has("usage") && jsonNode.get("usage").has("total_tokens")) {
                return jsonNode.get("usage").get("total_tokens").asInt();
            }
            
            return 0;
        } catch (Exception e) {
            log.warn("Failed to extract token usage from response", e);
            return 0;
        }
    }
    
    private String extractUserId(ServerRequest request) {
        return request.headers().firstHeader("X-User-ID")
            .or(() -> request.headers().firstHeader("Authorization")
                .map(this::extractUserIdFromAuth))
            .orElse("anonymous");
    }
    
    private String extractUserIdFromAuth(String authHeader) {
        // JWT 토큰이나 API Key에서 사용자 ID 추출
        // 실제 구현 필요
        return "user_from_auth";
    }
    
    private Map<String, Object> createRateLimitResponse() {
        Map<String, Object> error = new HashMap<>();
        error.put("error", Map.of(
            "message", "Rate limit exceeded. Please try again later.",
            "type", "rate_limit_exceeded",
            "code", "rate_limit_exceeded"
        ));
        return error;
    }
    
    private Map<String, Object> createErrorResponse(String message) {
        Map<String, Object> error = new HashMap<>();
        error.put("error", Map.of(
            "message", message,
            "type", "internal_error",
            "code", "internal_error"
        ));
        return error;
    }
}