package com.simpletokenlimiter.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * 토큰 사용량 데이터 모델
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TokenUsage {
    private String userId;
    private int tokensUsed;
    private LocalDateTime timestamp;
    private String requestId;
}