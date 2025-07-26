package com.simpletokenlimiter.service;

import com.simpletokenlimiter.config.TokenLimitConfig;
import com.simpletokenlimiter.exception.ModelServiceException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.reactive.function.server.ServerRequest;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;

/**
 * Llama 3.2 1B 모델 프록시 서비스
 */
@Service
@Slf4j
public class LlamaProxyService {
    
    private final WebClient webClient;
    private final TokenLimitService tokenLimitService;
    private final TokenLimitConfig config;
    private final ObjectMapper objectMapper;
    
    public LlamaProxyService(WebClient webClient, 
                           TokenLimitService tokenLimitService,
                           TokenLimitConfig config) {
        this.webClient = webClient;
        this.tokenLimitService = tokenLimitService;
        this.config = config;
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * vLLM으로 요청 프록시
     */
    public Mono<String> proxyToLlama(String requestBody, String userId) {
        String requestId = UUID.randomUUID().toString();
        int estimatedTokens = estimateTokensFromRequest(requestBody);
        
        return tokenLimitService.checkTokenLimit(userId, estimatedTokens)
            .then(forwardToVllm(requestBody))
            .flatMap(response -> {
                int actualTokens = extractTokenUsageFromResponse(response);
                return tokenLimitService.recordTokenUsage(userId, actualTokens, requestId)
                    .thenReturn(response);
            })
            .onErrorMap(WebClientResponseException.class, ex -> 
                new ModelServiceException("vLLM service error: " + ex.getMessage(), ex))
            .timeout(Duration.ofMinutes(2));
    }
    
    /**
     * 모델 상태 확인
     */
    public Mono<Boolean> checkHealth() {
        return webClient.get()
            .uri(config.getVllmUrl() + "/health")
            .retrieve()
            .toBodilessEntity()
            .map(response -> response.getStatusCode().is2xxSuccessful())
            .onErrorReturn(false)
            .timeout(Duration.ofSeconds(10));
    }
    
    /**
     * 사용 가능한 모델 조회
     */
    public Mono<String> getAvailableModels() {
        return webClient.get()
            .uri(config.getVllmUrl() + "/v1/models")
            .retrieve()
            .bodyToMono(String.class)
            .onErrorReturn("{\"data\": []}");
    }
    
    private Mono<String> forwardToVllm(String requestBody) {
        return webClient.post()
            .uri(config.getVllmUrl() + "/v1/chat/completions")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestBody)
            .retrieve()
            .bodyToMono(String.class);
    }
    
    private int estimateTokensFromRequest(String requestBody) {
        try {
            JsonNode jsonNode = objectMapper.readTree(requestBody);
            
            if (jsonNode.has("max_tokens")) {
                return jsonNode.get("max_tokens").asInt();
            }
            
            // 메시지 길이 기반 추정
            if (jsonNode.has("messages")) {
                int totalChars = 0;
                JsonNode messages = jsonNode.get("messages");
                for (JsonNode message : messages) {
                    if (message.has("content")) {
                        totalChars += message.get("content").asText().length();
                    }
                }
                return Math.max(totalChars / 4, 50); // 대략 4글자당 1토큰
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
}