package com.simpletokenlimiter.controller;

import com.simpletokenlimiter.service.LlamaProxyService;
import com.simpletokenlimiter.service.TokenLimitService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Llama 3.2 1B 모델 API 컨트롤러
 */
@RestController
@RequestMapping("/api/v1")
@Slf4j
public class LlamaController {
    
    private final LlamaProxyService llamaProxyService;
    private final TokenLimitService tokenLimitService;
    
    public LlamaController(LlamaProxyService llamaProxyService, 
                         TokenLimitService tokenLimitService) {
        this.llamaProxyService = llamaProxyService;
        this.tokenLimitService = tokenLimitService;
    }
    
    /**
     * 채팅 완성 API
     */
    @PostMapping(value = "/chat/completions", 
                consumes = MediaType.APPLICATION_JSON_VALUE,
                produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<String>> chatCompletions(
            @RequestBody String requestBody,
            @RequestHeader(value = "X-User-ID", defaultValue = "anonymous") String userId) {
        
        log.info("Chat completion request from user: {}", userId);
        
        return llamaProxyService.proxyToLlama(requestBody, userId)
            .map(response -> ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(response))
            .onErrorReturn(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("{\"error\": \"Internal server error\"}"));
    }
    
    /**
     * 모델 목록 조회
     */
    @GetMapping("/models")
    public Mono<ResponseEntity<String>> getModels() {
        return llamaProxyService.getAvailableModels()
            .map(models -> ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(models))
            .onErrorReturn(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body("{\"error\": \"Model service unavailable\"}"));
    }
    
    /**
     * 모델 상태 확인
     */
    @GetMapping("/health")
    public Mono<ResponseEntity<Map<String, Object>>> getHealth() {
        return llamaProxyService.checkHealth()
            .map(healthy -> ResponseEntity.ok(Map.of(
                "status", healthy ? "healthy" : "unhealthy",
                "model", "llama3.2-1b",
                "timestamp", System.currentTimeMillis()
            )));
    }
    
    /**
     * 사용자별 토큰 사용량 조회
     */
    @GetMapping("/usage/{userId}")
    public Mono<ResponseEntity<Map<String, Integer>>> getTokenUsage(
            @PathVariable String userId) {
        
        return tokenLimitService.getCurrentTokenUsage(userId)
            .map(usage -> ResponseEntity.ok(usage))
            .onErrorReturn(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .build());
    }
}