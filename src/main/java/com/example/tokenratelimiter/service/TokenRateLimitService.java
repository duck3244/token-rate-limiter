package com.example.tokenratelimiter.service;

import com.example.tokenratelimiter.config.TokenRateLimitConfig;
import com.example.tokenratelimiter.model.TokenUsage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Token Rate Limiting 서비스
 */
@Service
@Slf4j
public class TokenRateLimitService {
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final TokenRateLimitConfig config;
    
    // Redis key 패턴
    private static final String TOKEN_USAGE_KEY = "token:usage:%s:%s:%s"; // modelId:userId:timeWindow
    private static final String CONCURRENT_REQUEST_KEY = "token:concurrent:%s:%s"; // modelId:userId
    
    public TokenRateLimitService(RedisTemplate<String, Object> redisTemplate, 
                               TokenRateLimitConfig config) {
        this.redisTemplate = redisTemplate;
        this.config = config;
    }
    
    /**
     * 토큰 사용 전 제한 확인
     */
    public Mono<Boolean> checkTokenLimit(String modelId, String userId, int requestedTokens) {
        return Mono.fromCallable(() -> {
            TokenRateLimitConfig.ModelLimitConfig modelConfig = 
                config.getModels().getOrDefault(modelId, new TokenRateLimitConfig.ModelLimitConfig());
            
            // 동시 요청 수 확인
            if (!checkConcurrentRequests(modelId, userId, modelConfig.getMaxConcurrentRequests())) {
                log.warn("Concurrent request limit exceeded for model: {} user: {}", modelId, userId);
                return false;
            }
            
            // 분당 토큰 사용량 확인
            if (!checkTokenUsageInWindow(modelId, userId, "minute", 
                    requestedTokens, modelConfig.getMaxTokensPerMinute(), 60)) {
                log.warn("Per-minute token limit exceeded for model: {} user: {}", modelId, userId);
                return false;
            }
            
            // 시간당 토큰 사용량 확인
            if (!checkTokenUsageInWindow(modelId, userId, "hour", 
                    requestedTokens, modelConfig.getMaxTokensPerHour(), 3600)) {
                log.warn("Per-hour token limit exceeded for model: {} user: {}", modelId, userId);
                return false;
            }
            
            // 일당 토큰 사용량 확인
            if (!checkTokenUsageInWindow(modelId, userId, "day", 
                    requestedTokens, modelConfig.getMaxTokensPerDay(), 86400)) {
                log.warn("Per-day token limit exceeded for model: {} user: {}", modelId, userId);
                return false;
            }
            
            return true;
        }).subscribeOn(Schedulers.boundedElastic());
    }
    
    /**
     * 토큰 사용량 기록
     */
    public Mono<Void> recordTokenUsage(String modelId, String userId, int tokensUsed, String requestId) {
        return Mono.fromRunnable(() -> {
            TokenUsage usage = new TokenUsage(modelId, userId, tokensUsed, 
                                            LocalDateTime.now(), requestId);
            
            // 각 시간 윈도우별로 토큰 사용량 누적
            updateTokenUsageInWindow(modelId, userId, "minute", tokensUsed, 60);
            updateTokenUsageInWindow(modelId, userId, "hour", tokensUsed, 3600);
            updateTokenUsageInWindow(modelId, userId, "day", tokensUsed, 86400);
            
            // 동시 요청 카운트 감소
            decrementConcurrentRequests(modelId, userId);
            
            log.debug("Recorded token usage: {} tokens for model: {} user: {}", 
                     tokensUsed, modelId, userId);
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }
    
    /**
     * 동시 요청 수 확인 및 증가
     */
    private boolean checkConcurrentRequests(String modelId, String userId, int maxConcurrent) {
        String key = String.format(CONCURRENT_REQUEST_KEY, modelId, userId);
        
        return redisTemplate.execute((RedisCallback<Boolean>) connection -> {
            byte[] keyBytes = key.getBytes();
            byte[] currentBytes = connection.get(keyBytes);
            
            int current = 0;
            if (currentBytes != null) {
                current = Integer.parseInt(new String(currentBytes));
            }
            
            if (current >= maxConcurrent) {
                return false;
            }
            
            // 동시 요청 수 증가
            connection.incr(keyBytes);
            connection.expire(keyBytes, 300); // 5분 TTL
            return true;
        });
    }
    
    /**
     * 동시 요청 수 감소
     */
    private void decrementConcurrentRequests(String modelId, String userId) {
        String key = String.format(CONCURRENT_REQUEST_KEY, modelId, userId);
        redisTemplate.execute((RedisCallback<Object>) connection -> {
            byte[] keyBytes = key.getBytes();
            Long current = connection.decr(keyBytes);
            if (current != null && current <= 0) {
                connection.del(keyBytes);
            }
            return null;
        });
    }
    
    /**
     * 특정 시간 윈도우에서 토큰 사용량 확인
     */
    private boolean checkTokenUsageInWindow(String modelId, String userId, String window, 
                                          int requestedTokens, int maxTokens, int windowSeconds) {
        String key = String.format(TOKEN_USAGE_KEY, modelId, userId, window);
        
        return redisTemplate.execute((RedisCallback<Boolean>) connection -> {
            byte[] keyBytes = key.getBytes();
            byte[] currentBytes = connection.get(keyBytes);
            
            int currentUsage = 0;
            if (currentBytes != null) {
                currentUsage = Integer.parseInt(new String(currentBytes));
            }
            
            return (currentUsage + requestedTokens) <= maxTokens;
        });
    }
    
    /**
     * 시간 윈도우별 토큰 사용량 업데이트
     */
    private void updateTokenUsageInWindow(String modelId, String userId, String window, 
                                        int tokensUsed, int windowSeconds) {
        String key = String.format(TOKEN_USAGE_KEY, modelId, userId, window);
        
        redisTemplate.execute((RedisCallback<Object>) connection -> {
            byte[] keyBytes = key.getBytes();
            connection.incrBy(keyBytes, tokensUsed);
            connection.expire(keyBytes, windowSeconds);
            return null;
        });
    }
    
    /**
     * 현재 토큰 사용량 조회
     */
    public Mono<Map<String, Integer>> getCurrentTokenUsage(String modelId, String userId) {
        return Mono.fromCallable(() -> {
            Map<String, Integer> usage = new HashMap<>();
            
            String minuteKey = String.format(TOKEN_USAGE_KEY, modelId, userId, "minute");
            String hourKey = String.format(TOKEN_USAGE_KEY, modelId, userId, "hour");
            String dayKey = String.format(TOKEN_USAGE_KEY, modelId, userId, "day");
            
            usage.put("minute", getTokenUsageFromRedis(minuteKey));
            usage.put("hour", getTokenUsageFromRedis(hourKey));
            usage.put("day", getTokenUsageFromRedis(dayKey));
            
            return usage;
        }).subscribeOn(Schedulers.boundedElastic());
    }
    
    private Integer getTokenUsageFromRedis(String key) {
        Object value = redisTemplate.opsForValue().get(key);
        return value != null ? Integer.parseInt(value.toString()) : 0;
    }
}