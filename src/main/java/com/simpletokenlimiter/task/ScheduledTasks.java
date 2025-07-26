package com.simpletokenlimiter.task;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * 스케줄링된 작업들
 */
@Component
@Slf4j
public class ScheduledTasks {
    
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    
    public ScheduledTasks(ReactiveRedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }
    
    /**
     * 매시간 만료된 키 정리
     */
    @Scheduled(fixedRate = 3600000) // 1시간마다
    public void cleanupExpiredKeys() {
        log.info("Starting cleanup of expired keys");
        
        redisTemplate.keys("token:usage:*")
            .flatMap(key -> redisTemplate.hasKey(key)
                .filter(exists -> !exists)
                .flatMap(exists -> redisTemplate.delete(key)))
            .count()
            .subscribe(deletedCount -> 
                log.info("Cleaned up {} expired keys", deletedCount));
    }
    
    /**
     * 매 30분마다 시스템 상태 확인
     */
    @Scheduled(fixedRate = 1800000) // 30분마다
    public void healthCheck() {
        log.debug("Performing system health check");
        
        redisTemplate.execute(connection -> connection.ping())
            .subscribe(
                result -> log.debug("Redis health check: {}", result),
                error -> log.warn("Redis health check failed", error)
            );
    }
    
    /**
     * 매일 자정에 통계 수집
     */
    @Scheduled(cron = "0 0 0 * * ?") // 매일 자정
    public void collectDailyStats() {
        log.info("Collecting daily statistics");
        
        redisTemplate.keys("token:usage:*:day")
            .count()
            .subscribe(count -> 
                log.info("Active daily usage keys: {}", count));
    }
}