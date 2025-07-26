package com.example.tokenratelimiter.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * Token 사용량 추적을 위한 데이터 클래스
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TokenUsage {
    private String modelId;
    private String userId;
    private int tokensUsed;
    private LocalDateTime timestamp;
    private String requestId;
}