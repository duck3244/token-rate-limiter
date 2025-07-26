package com.example.tokenratelimiter.controller;

import com.example.tokenratelimiter.service.TokenRateLimitService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 토큰 사용량 모니터링 컨트롤러
 */
@RestController
@RequestMapping("/api/v1/admin/token-usage")
@Slf4j
public class TokenUsageController {
    
    private final TokenRateLimitService rateLimitService;
    
    public TokenUsageController(TokenRateLimitService rateLimitService) {
        this.rateLimitService = rateLimitService;
    }
    
    /**
     * 특정 모델/사용자의 토큰 사용량 조회
     */
    @GetMapping("/{modelId}/{userId}")
    public Mono<ResponseEntity<Map<String, Integer>>> getTokenUsage(
            @PathVariable String modelId,
            @PathVariable String userId) {
        
        return rateLimitService.getCurrentTokenUsage(modelId, userId)
            .map(usage -> ResponseEntity.ok(usage))
            .onErrorReturn(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
    }
    
    /**
     * 모든 모델의 토큰 사용량 요약
     */
    @GetMapping("/summary")
    public Mono<ResponseEntity<Map<String, Object>>> getUsageSummary() {
        // TODO: 모든 모델/사용자의 사용량 집계 로직 구현
        return Mono.just(ResponseEntity.ok(Map.of("message", "Summary endpoint - implementation needed")));
    }
    
    /**
     * 특정 사용자의 제한 설정 조회
     */
    @GetMapping("/limits/{modelId}/{userId}")
    public Mono<ResponseEntity<Map<String, Object>>> getUserLimits(
            @PathVariable String modelId,
            @PathVariable String userId) {
        
        // TODO: 사용자별 제한 설정 조회 로직 구현
        return Mono.just(ResponseEntity.ok(Map.of(
            "modelId", modelId,
            "userId", userId,
            "message", "User limits endpoint - implementation needed"
        )));
    }
    
    /**
     * 서비스 상태 확인
     */
    @GetMapping("/health")
    public Mono<ResponseEntity<Map<String, Object>>> health() {
        return Mono.just(ResponseEntity.ok(Map.of(
            "status", "healthy",
            "service", "token-rate-limiting",
            "timestamp", System.currentTimeMillis()
        )));
    }
    
    /**
     * 토큰 사용량 리셋 (관리자용)
     */
    @PostMapping("/reset/{modelId}/{userId}")
    public Mono<ResponseEntity<Map<String, Object>>> resetTokenUsage(
            @PathVariable String modelId,
            @PathVariable String userId,
            @RequestParam(defaultValue = "all") String window) {
        
        // TODO: 토큰 사용량 리셋 로직 구현
        return Mono.just(ResponseEntity.ok(Map.of(
            "message", "Token usage reset",
            "modelId", modelId,
            "userId", userId,
            "window", window
        )));
    }
}