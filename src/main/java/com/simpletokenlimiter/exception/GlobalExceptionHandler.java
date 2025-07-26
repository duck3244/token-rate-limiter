package com.simpletokenlimiter.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 글로벌 예외 처리 핸들러
 */
@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    
    /**
     * 토큰 제한 초과 예외 처리
     */
    @ExceptionHandler(TokenLimitExceededException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleTokenLimitExceeded(
            TokenLimitExceededException ex) {
        
        log.warn("Token limit exceeded: {} (retry after: {} seconds)", 
                ex.getMessage(), ex.getRetryAfter());
        
        Map<String, Object> errorResponse = Map.of(
            "error", "rate_limit_exceeded",
            "message", ex.getMessage(),
            "retry_after", ex.getRetryAfter(),
            "limit_type", ex.getLimitType(),
            "timestamp", LocalDateTime.now()
        );
        
        return Mono.just(ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(ex.getRetryAfter()))
                .body(errorResponse));
    }
    
    /**
     * 모델 서비스 예외 처리
     */
    @ExceptionHandler(ModelServiceException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleModelServiceException(
            ModelServiceException ex) {
        
        log.error("Model service error: {}", ex.getMessage());
        
        Map<String, Object> errorResponse = Map.of(
            "error", "model_service_error",
            "message", "Model service is temporarily unavailable",
            "timestamp", LocalDateTime.now()
        );
        
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(errorResponse));
    }
    
    /**
     * 일반적인 예외 처리
     */
    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleGenericException(
            Exception ex) {
        
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        
        Map<String, Object> errorResponse = Map.of(
            "error", "internal_server_error",
            "message", "An unexpected error occurred",
            "timestamp", LocalDateTime.now()
        );
        
        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorResponse));
    }
}