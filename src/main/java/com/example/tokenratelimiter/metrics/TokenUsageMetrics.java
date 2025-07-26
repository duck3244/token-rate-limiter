package com.example.tokenratelimiter.metrics;

import io.micrometer.core.instrument.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 메트릭스 수집을 위한 컴포넌트
 */
@Component
@Slf4j
public class TokenUsageMetrics {
    
    private final MeterRegistry meterRegistry;
    private final RedisTemplate<String, Object> redisTemplate;
    private final Counter tokenUsageCounter;
    private final Counter rateLimitExceededCounter;
    private final Timer requestTimer;
    private final Gauge currentTokenUsage;
    
    public TokenUsageMetrics(MeterRegistry meterRegistry, RedisTemplate<String, Object> redisTemplate) {
        this.meterRegistry = meterRegistry;
        this.redisTemplate = redisTemplate;
        
        // 토큰 사용량 카운터
        this.tokenUsageCounter = Counter.builder("token.usage.total")
            .description("Total tokens consumed by model and user")
            .register(meterRegistry);
        
        // Rate limit 초과 카운터
        this.rateLimitExceededCounter = Counter.builder("token.rate.limit.exceeded.total")
            .description("Total number of rate limit exceeded events")
            .register(meterRegistry);
        
        // 요청 처리 시간 타이머
        this.requestTimer = Timer.builder("model.request.duration")
            .description("Model request processing time")
            .register(meterRegistry);
        
        // 현재 토큰 사용량 게이지
        this.currentTokenUsage = Gauge.builder("token.usage.current")
            .description("Current token usage in time window")
            .register(meterRegistry, this, TokenUsageMetrics::getCurrentUsage);
    }
    
    /**
     * 토큰 사용량 기록
     */
    public void recordTokenUsage(String modelId, String userId, int tokens) {
        tokenUsageCounter.increment(
            Tags.of(
                Tag.of("model", modelId),
                Tag.of("user", userId)
            ),
            tokens
        );
        
        log.debug("Recorded token usage metric: {} tokens for model: {} user: {}", 
                 tokens, modelId, userId);
    }
    
    /**
     * Rate limit 초과 이벤트 기록
     */
    public void recordRateLimitExceeded(String modelId, String userId, String limitType) {
        rateLimitExceededCounter.increment(
            Tags.of(
                Tag.of("model", modelId),
                Tag.of("user", userId),
                Tag.of("limit_type", limitType)
            )
        );
        
        log.warn("Rate limit exceeded for model: {} user: {} type: {}", 
                modelId, userId, limitType);
    }
    
    /**
     * 요청 처리 시간 기록
     */
    public void recordRequestTime(String modelId, long durationMillis) {
        requestTimer.record(Duration.ofMillis(durationMillis), 
                          Tags.of(Tag.of("model", modelId)));
    }
    
    /**
     * 요청 처리 시간 측정을 위한 타이머 샘플 시작
     */
    public Timer.Sample startRequestTimer() {
        return Timer.start(meterRegistry);
    }
    
    /**
     * 현재 활성 요청 수 기록
     */
    public void recordActiveRequests(String modelId, String userId, int count) {
        Gauge.builder("token.concurrent.requests")
            .description("Current number of concurrent requests")
            .tag("model", modelId)
            .tag("user", userId)
            .register(meterRegistry, () -> count);
    }
    
    /**
     * Redis에서 현재 토큰 사용량 조회 (Gauge용)
     */
    private double getCurrentUsage() {
        try {
            // 전체 시스템의 현재 토큰 사용량 계산
            // 실제 구현에서는 Redis에서 모든 활성 사용량을 집계
            return 0.0; // 임시값
        } catch (Exception e) {
            log.warn("Failed to get current token usage for metrics", e);
            return 0.0;
        }
    }
    
    /**
     * 커스텀 메트릭 등록
     */
    public void registerCustomMetric(String name, String description, Tags tags, Number value) {
        Gauge.builder(name)
            .description(description)
            .tags(tags)
            .register(meterRegistry, () -> value);
    }
}