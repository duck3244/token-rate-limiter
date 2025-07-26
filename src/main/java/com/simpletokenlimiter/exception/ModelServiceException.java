package com.simpletokenlimiter.exception;

/**
 * 모델 서비스 사용 불가 예외
 */
public class ModelServiceException extends RuntimeException {
    public ModelServiceException(String message) {
        super(message);
    }
    
    public ModelServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}