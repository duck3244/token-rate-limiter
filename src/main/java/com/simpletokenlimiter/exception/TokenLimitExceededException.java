package com.simpletokenlimiter.exception;

/**
 * 토큰 제한 초과 예외
 */
public class TokenLimitExceededException extends RuntimeException {
    private final int retryAfter;
    private final String limitType;
    
    public TokenLimitExceededException(String message, int retryAfter, String limitType) {
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