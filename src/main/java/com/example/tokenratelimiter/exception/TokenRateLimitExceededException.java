package com.example.tokenratelimiter.exception;

/**
 * 토큰 사용량 제한을 초과했을 때 발생하는 예외
 */
public class TokenRateLimitExceededException extends RuntimeException {
    private final int retryAfter;
    private final String limitType;
    
    public TokenRateLimitExceededException(String message, int retryAfter) {
        super(message);
        this.retryAfter = retryAfter;
        this.limitType = "unknown";
    }
    
    public TokenRateLimitExceededException(String message, int retryAfter, String limitType) {
        super(message);
        this.retryAfter = retryAfter;
        this.limitType = limitType;
    }
    
    public int getRetryAfter() {
        return retryAfter;
    }
    
    public String getLimitType() {
        return limitType;
    }
}
