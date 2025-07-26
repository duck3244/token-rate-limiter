package com.example.tokenratelimiter.task;

import com.example.tokenratelimiter.metrics.TokenUsageMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 스케줄링된 작업들
 */
@Component
@Slf4j
public class TokenUsageScheduledTasks {
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final TokenUsageMetrics metrics;
    
    public TokenUsageScheduledTasks(RedisTemplate<String, Object> redisTemplate,
                                  TokenUsageMetrics metrics) {
        this.redisTemplate = redisTemplate;
        this.metrics = metrics;
    }
    
    /**
     * 매시간 토큰 사용량 통계 수집
     */
    @Scheduled(fixedRate = 3600000) // 1시간마다
    public void collectHourlyStats() {
        log.info("Starting hourly token usage statistics collection");
        
        try {
            // Redis에서 모든 모델/사용자별 통계 수집
            Set<String> keys = redisTemplate.keys("token:usage:*:*:hour");
            
            if (keys != null && !keys.isEmpty()) {
                int processedKeys = 0;
                
                for (String key : keys) {
                    try {
                        String[] parts = key.split(":");
                        if (parts.length >= 5) {
                            String modelId = parts[2];
                            String userId = parts[3];
                            Object usage = redisTemplate.opsForValue().get(key);
                            
                            if (usage != null) {
                                int tokens = Integer.parseInt(usage.toString());
                                metrics.recordTokenUsage(modelId, userId, tokens);
                                processedKeys++;
                            }
                        }
                    } catch (Exception e) {
                        log.warn("Failed to process key: {}", key, e);
                    }
                }
                
                log.info("Processed {} token usage statistics", processedKeys);
            } else {
                log.debug("No token usage statistics found for this hour");
            }
        } catch (Exception e) {
            log.error("Failed to collect hourly statistics", e);
        }
    }
    
    /**
     * 매일 자정에 만료된 키 정리
     */
    @Scheduled(cron = "0 0 0 * * ?") // 매일 자정
    public void cleanupExpiredKeys() {
        log.info("Starting cleanup of expired token usage keys");
        
        try {
            // 일별 토큰 사용량 키 중 만료된 것들 정리
            Set<String> dayKeys = redisTemplate.keys("token:usage:*:*:day");
            
            if (dayKeys != null && !dayKeys.isEmpty()) {
                long sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000);
                int deletedCount = 0;
                
                for (String key : dayKeys) {
                    try {
                        Long ttl = redisTemplate.getExpire(key);
                        if (ttl != null && ttl < sevenDaysAgo) {
                            redisTemplate.delete(key);
                            deletedCount++;
                        }
                    } catch (Exception e) {
                        log.warn("Failed to check/delete key: {}", key, e);
                    }
                }
                
                log.info("Cleaned up {} expired token usage keys", deletedCount);
            }
            
            // 동시 요청 키 중 오래된 것들 정리
            Set<String> concurrentKeys = redisTemplate.keys("token:concurrent:*");
            if (concurrentKeys != null && !concurrentKeys.isEmpty()) {
                int cleanedConcurrent = 0;
                
                for (String key : concurrentKeys) {
                    try {
                        Object value = redisTemplate.opsForValue().get(key);
                        if (value == null || "0".equals(value.toString())) {
                            redisTemplate.delete(key);
                            cleanedConcurrent++;
                        }
                    } catch (Exception e) {
                        log.warn("Failed to clean concurrent key: {}", key, e);
                    }
                }
                
                log.info("Cleaned up {} empty concurrent request keys", cleanedConcurrent);
            }
            
        } catch (Exception e) {
            log.error("Failed to cleanup expired keys", e);
        }
    }
    
    /**
     * 매 5분마다 시스템 상태 확인
     */
    @Scheduled(fixedRate = 300000) // 5분마다
    public void healthCheck() {
        log.debug("Performing system health check");
        
        try {
            // Redis 연결 상태 확인
            String pingResult = redisTemplate.execute(connection -> 
                new String(connection.ping()));
            
            if (!"PONG".equals(pingResult)) {
                log.warn("Redis health check failed: {}", pingResult);
            }
            
            // 활성 토큰 사용량 키 개수 확인
            Set<String> activeKeys = redisTemplate.keys("token:usage:*");
            int activeKeyCount = activeKeys != null ? activeKeys.size() : 0;
            
            log.debug("System health check completed. Active token usage keys: {}", activeKeyCount);
            
            // 메트릭으로 기록
            metrics.registerCustomMetric(
                "system.active.token.keys", 
                "Number of active token usage keys", 
                io.micrometer.core.instrument.Tags.empty(), 
                activeKeyCount
            );
            
        } catch (Exception e) {
            log.error("Health check failed", e);
        }
    }
    
    /**
     * 매 30분마다 통계 요약 로그 출력
     */
    @Scheduled(fixedRate = 1800000) // 30분마다
    public void logStatisticsSummary() {
        try {
            Set<String> minuteKeys = redisTemplate.keys("token:usage:*:*:minute");
            Set<String> hourKeys = redisTemplate.keys("token:usage:*:*:hour");
            Set<String> dayKeys = redisTemplate.keys("token:usage:*:*:day");
            Set<String> concurrentKeys = redisTemplate.keys("token:concurrent:*");
            
            int minuteKeyCount = minuteKeys != null ? minuteKeys.size() : 0;
            int hourKeyCount = hourKeys != null ? hourKeys.size() : 0;
            int dayKeyCount = dayKeys != null ? dayKeys.size() : 0;
            int concurrentKeyCount = concurrentKeys != null ? concurrentKeys.size() : 0;
            
            log.info("Token usage statistics summary - Minute: {}, Hour: {}, Day: {}, Concurrent: {}", 
                    minuteKeyCount, hourKeyCount, dayKeyCount, concurrentKeyCount);
            
        } catch (Exception e) {
            log.warn("Failed to generate statistics summary", e);
        }
    }
}