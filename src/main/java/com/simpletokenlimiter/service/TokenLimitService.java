package com.simpletokenlimiter.service;

import com.simpletokenlimiter.config.TokenLimitConfig;
import com.simpletokenlimiter.exception.TokenLimitExceededException;
import com.simpletokenlimiter.model.TokenUsage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 토큰 제한 서비스
 */
@Service
@Slf4j
public class TokenLimitService {
    
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final TokenLimitConfig config;
    
    private static final String TOKEN_KEY = "token:usage:%s:%s"; // userId:window
    private static final String CONCURRENT_KEY = "concurrent:%s"; // userId
    
    public TokenLimitService(ReactiveRedisTemplate<String, String> redisTemplate, 
                           TokenLimitConfig config) {
        this.redisTemplate = redisTemplate;
        this.config = config;
    }
    
    /**
     * 토큰 사용 전 제한 확인
     */
    public Mono<Boolean> checkTokenLimit(String userId, int requestedTokens) {
        return checkConcurrentRequests(userId)
            .flatMap(allowed -> {
                if (!allowed) {
                    return Mono.error(new TokenLimitExceededException(
                        "Concurrent request limit exceeded", 30, "concurrent"));
                }
                return checkTokenUsageWindows(userId, requestedTokens);
            })
            .flatMap(allowed -> {
                if (!allowed) {
                    return Mono.error(new TokenLimitExceededException(
                        "Token usage limit exceeded", 60, "rate"));
                }
                return incrementConcurrentRequests(userId);
            });
    }
    
    /**
     * 토큰 사용량 기록
     */
    public Mono<Void> recordTokenUsage(String userId, int tokensUsed, String requestId) {
        TokenUsage usage = new TokenUsage(userId, tokensUsed, LocalDateTime.now(), requestId);
        
        return Mono.when(
            updateTokenUsageWindow(userId, "minute", tokensUsed, 60),
            updateTokenUsageWindow(userId, "hour", tokensUsed, 3600),
            updateTokenUsageWindow(userId, "day", tokensUsed, 86400)
        ).then(decrementConcurrentRequests(userId))
        .doOnSuccess(v -> log.info("Recorded {} tokens for user: {}", tokensUsed, userId));
    }
    
    /**
     * 현재 토큰 사용량 조회
     */
    public Mono<Map<String, Integer>> getCurrentTokenUsage(String userId) {
        return Mono.fromCallable(() -> {
            Map<String, Integer> usage = new HashMap<>();
            return usage;
        }).flatMap(usage -> 
            getTokenUsage(userId, "minute")
                .defaultIfEmpty(0)
                .doOnNext(minute -> usage.put("minute", minute))
                .then(getTokenUsage(userId, "hour"))
                .defaultIfEmpty(0)
                .doOnNext(hour -> usage.put("hour", hour))
                .then(getTokenUsage(userId, "day"))
                .defaultIfEmpty(0)
                .doOnNext(day -> usage.put("day", day))
                .thenReturn(usage)
        );
    }
    
    private Mono<Boolean> checkConcurrentRequests(String userId) {
        String key = String.format(CONCURRENT_KEY, userId);
        return redisTemplate.opsForValue().get(key)
            .map(Integer::parseInt)
            .defaultIfEmpty(0)
            .map(current -> current < config.getMaxConcurrentRequests());
    }
    
    private Mono<Boolean> incrementConcurrentRequests(String userId) {
        String key = String.format(CONCURRENT_KEY, userId);
        return redisTemplate.opsForValue().increment(key)
            .flatMap(count -> redisTemplate.expire(key, Duration.ofMinutes(5))
                .thenReturn(true));
    }
    
    private Mono<Void> decrementConcurrentRequests(String userId) {
        String key = String.format(CONCURRENT_KEY, userId);
        return redisTemplate.opsForValue().decrement(key)
            .flatMap(count -> {
                if (count <= 0) {
                    return redisTemplate.delete(key).then();
                }
                return Mono.empty();
            });
    }
    
    private Mono<Boolean> checkTokenUsageWindows(String userId, int requestedTokens) {
        return Mono.when(
            checkTokenUsageWindow(userId, "minute", requestedTokens, config.getMaxTokensPerMinute()),
            checkTokenUsageWindow(userId, "hour", requestedTokens, config.getMaxTokensPerHour()),
            checkTokenUsageWindow(userId, "day", requestedTokens, config.getMaxTokensPerDay())
        ).thenReturn(true).onErrorReturn(false);
    }
    
    private Mono<Boolean> checkTokenUsageWindow(String userId, String window, 
                                              int requestedTokens, int maxTokens) {
        return getTokenUsage(userId, window)
            .defaultIfEmpty(0)
            .map(current -> (current + requestedTokens) <= maxTokens);
    }
    
    private Mono<Void> updateTokenUsageWindow(String userId, String window, 
                                            int tokens, int ttlSeconds) {
        String key = String.format(TOKEN_KEY, userId, window);
        return redisTemplate.opsForValue().increment(key, tokens)
            .flatMap(count -> redisTemplate.expire(key, Duration.ofSeconds(ttlSeconds)))
            .then();
    }
    
    private Mono<Integer> getTokenUsage(String userId, String window) {
        String key = String.format(TOKEN_KEY, userId, window);
        return redisTemplate.opsForValue().get(key)
            .map(Integer::parseInt);
    }
}