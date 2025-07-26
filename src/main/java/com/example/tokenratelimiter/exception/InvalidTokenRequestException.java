package com.example.tokenratelimiter.exception;

/**
 * 잘못된 토큰 요청일 때 발생하는 예외
 */
public class InvalidTokenRequestException extends RuntimeException {
    public InvalidTokenRequestException(String message) {
        super(message);
    }
    
    public InvalidTokenRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}