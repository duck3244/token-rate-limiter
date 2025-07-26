import com.simpletokenlimiter.config.TokenLimitConfig;
import com.simpletokenlimiter.exception.TokenLimitExceededException;
import com.simpletokenlimiter.service.TokenLimitService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * TokenLimitService 테스트
 */
@ExtendWith(MockitoExtension.class)
class TokenLimitServiceTest {
    
    @Mock
    private ReactiveRedisTemplate<String, String> redisTemplate;
    
    @Mock
    private ReactiveValueOperations<String, String> valueOperations;
    
    private TokenLimitService tokenLimitService;
    private TokenLimitConfig config;
    
    @BeforeEach
    void setUp() {
        config = new TokenLimitConfig();
        config.setMaxTokensPerMinute(1000);
        config.setMaxTokensPerHour(10000);
        config.setMaxTokensPerDay(100000);
        config.setMaxConcurrentRequests(5);
        
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        
        tokenLimitService = new TokenLimitService(redisTemplate, config);
    }
    
    @Test
    void testCheckTokenLimit_Success() {
        // Given
        String userId = "test-user";
        int requestedTokens = 100;
        
        when(valueOperations.get(anyString())).thenReturn(Mono.just("0"));
        when(valueOperations.increment(anyString())).thenReturn(Mono.just(1L));
        when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(Mono.just(true));
        
        // When & Then
        StepVerifier.create(tokenLimitService.checkTokenLimit(userId, requestedTokens))
            .expectNext(true)
            .verifyComplete();
    }
    
    @Test
    void testCheckTokenLimit_ConcurrentLimitExceeded() {
        // Given
        String userId = "test-user";
        int requestedTokens = 100;
        
        when(valueOperations.get(anyString())).thenReturn(Mono.just("5")); // 최대치 도달
        
        // When & Then
        StepVerifier.create(tokenLimitService.checkTokenLimit(userId, requestedTokens))
            .expectError(TokenLimitExceededException.class)
            .verify();
    }
    
    @Test
    void testRecordTokenUsage_Success() {
        // Given
        String userId = "test-user";
        int tokensUsed = 50;
        String requestId = "req-123";
        
        when(valueOperations.increment(anyString(), any(Long.class))).thenReturn(Mono.just(50L));
        when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(Mono.just(true));
        when(valueOperations.decrement(anyString())).thenReturn(Mono.just(0L));
        when(redisTemplate.delete(anyString())).thenReturn(Mono.just(1L));
        
        // When & Then
        StepVerifier.create(tokenLimitService.recordTokenUsage(userId, tokensUsed, requestId))
            .verifyComplete();
    }
}