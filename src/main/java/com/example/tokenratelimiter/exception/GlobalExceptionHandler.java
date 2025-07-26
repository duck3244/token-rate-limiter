package com.example.tokenratelimiter.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 예외 처리를 위한 글로벌 에러 핸들러
 */
@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    
    /**
     * 토큰 사용량 제한 초과 예외 처리
     */
    @ExceptionHandler(TokenRateLimitExceededException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleRateLimitExceeded(
            TokenRateLimitExceededException ex, ServerWebExchange exchange) {
        
        log.warn("Rate limit exceeded: {} (retry after: {} seconds, type: {})", 
                ex.getMessage(), ex.getRetryAfter(), ex.getLimitType());
        
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", "rate_limit_exceeded");
        errorResponse.put("message", ex.getMessage());
        errorResponse.put("retry_after", ex.getRetryAfter());
        errorResponse.put("limit_type", ex.getLimitType());
        errorResponse.put("timestamp", LocalDateTime.now());
        
        return Mono.just(ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                                     .header("Retry-After", String.valueOf(ex.getRetryAfter()))
                                     .body(errorResponse));
    }
    
    /**
     * 모델을 찾을 수 없는 예외 처리
     */
    @ExceptionHandler(ModelNotFoundException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleModelNotFound(
            ModelNotFoundException ex, ServerWebExchange exchange) {
        
        log.warn("Model not found: {}", ex.getMessage());
        
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", "model_not_found");
        errorResponse.put("message", ex.getMessage());
        errorResponse.put("timestamp", LocalDateTime.now());
        
        return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND)
                                     .body(errorResponse));
    }
    
    /**
     * 모델 서비스 사용 불가 예외 처리
     */
    @ExceptionHandler(ModelServiceUnavailableException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleModelServiceUnavailable(
            ModelServiceUnavailableException ex, ServerWebExchange exchange) {
        
        log.error("Model service unavailable: {}", ex.getMessage());
        
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", "service_unavailable");
        errorResponse.put("message", ex.getMessage());
        errorResponse.put("timestamp", LocalDateTime.now());
        
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                                     .body(errorResponse));
    }
    
    /**
     * 잘못된 토큰 요청 예외 처리
     */
    @ExceptionHandler(InvalidTokenRequestException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleInvalidTokenRequest(
            InvalidTokenRequestException ex, ServerWebExchange exchange) {
        
        log.warn("Invalid token request: {}", ex.getMessage());
        
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", "invalid_request");
        errorResponse.put("message", ex.getMessage());
        errorResponse.put("timestamp", LocalDateTime.now());
        
        return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                     .body(errorResponse));
    }
    
    /**
     * 일반적인 런타임 예외 처리
     */
    @ExceptionHandler(RuntimeException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleRuntimeException(
            RuntimeException ex, ServerWebExchange exchange) {
        
        log.error("Unexpected runtime exception: {}", ex.getMessage(), ex);
        
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", "internal_server_error");
        errorResponse.put("message", "An unexpected error occurred");
        errorResponse.put("timestamp", LocalDateTime.now());
        
        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                     .body(errorResponse));
    }
    
    /**
     * 모든 예외에 대한 기본 처리
     */
    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleGenericException(
            Exception ex, ServerWebExchange exchange) {
        
        log.error("Unexpected exception: {}", ex.getMessage(), ex);
        
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", "internal_server_error");
        errorResponse.put("message", "An unexpected error occurred");
        errorResponse.put("timestamp", LocalDateTime.now());
        
        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                     .body(errorResponse));
    }
}