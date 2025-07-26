package com.example.tokenratelimiter.exception;

/**
 * 모델 서비스가 사용할 수 없을 때 발생하는 예외
 */
public class ModelServiceUnavailableException extends RuntimeException {
    public ModelServiceUnavailableException(String modelId) {
        super("Model service unavailable: " + modelId);
    }
    
    public ModelServiceUnavailableException(String modelId, Throwable cause) {
        super("Model service unavailable: " + modelId, cause);
    }
}