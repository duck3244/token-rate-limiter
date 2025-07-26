package com.example.tokenratelimiter.exception;

/**
 * 모델을 찾을 수 없을 때 발생하는 예외
 */
public class ModelNotFoundException extends RuntimeException {
    public ModelNotFoundException(String modelId) {
        super("Model not found: " + modelId);
    }
    
    public ModelNotFoundException(String modelId, Throwable cause) {
        super("Model not found: " + modelId, cause);
    }
}
