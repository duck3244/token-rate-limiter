package com.example.tokenratelimiter.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 모델 레지스트리 서비스
 */
@Service
@Slf4j
public class ModelRegistryService {
    
    private final Map<String, String> modelEndpoints;
    private final RedisTemplate<String, Object> redisTemplate;
    private final WebClient webClient;
    
    public ModelRegistryService(@Value("#{${vllm.services}}") Map<String, String> modelEndpoints,
                              RedisTemplate<String, Object> redisTemplate,
                              WebClient.Builder webClientBuilder) {
        this.modelEndpoints = modelEndpoints;
        this.redisTemplate = redisTemplate;
        this.webClient = webClientBuilder.build();
    }
    
    /**
     * 모델 엔드포인트 조회
     */
    public Mono<String> getModelEndpoint(String modelId) {
        return Mono.fromCallable(() -> {
            // 먼저 캐시에서 확인
            String cachedEndpoint = (String) redisTemplate.opsForValue()
                .get("model:endpoint:" + modelId);
            
            if (cachedEndpoint != null) {
                return cachedEndpoint;
            }
            
            // 설정에서 확인
            String endpoint = modelEndpoints.get(modelId);
            if (endpoint != null) {
                // 캐시에 저장 (5분 TTL)
                redisTemplate.opsForValue().set("model:endpoint:" + modelId, 
                                              endpoint, Duration.ofMinutes(5));
                return endpoint;
            }
            
            return null;
        }).subscribeOn(Schedulers.boundedElastic());
    }
    
    /**
     * 사용 가능한 모델 목록 조회
     */
    public Mono<List<String>> getAvailableModels() {
        return Mono.fromCallable(() -> new ArrayList<>(modelEndpoints.keySet()))
            .subscribeOn(Schedulers.boundedElastic());
    }
    
    /**
     * 모델 상태 확인
     */
    public Mono<Boolean> isModelHealthy(String modelId) {
        return getModelEndpoint(modelId)
            .flatMap(endpoint -> {
                return webClient.get()
                    .uri(endpoint + "/health")
                    .retrieve()
                    .toBodilessEntity()
                    .map(response -> response.getStatusCode().is2xxSuccessful())
                    .onErrorReturn(false)
                    .timeout(Duration.ofSeconds(5)); // 5초 타임아웃
            })
            .defaultIfEmpty(false);
    }
    
    /**
     * 모델 엔드포인트 등록/업데이트
     */
    public Mono<Void> registerModel(String modelId, String endpoint) {
        return Mono.fromRunnable(() -> {
            modelEndpoints.put(modelId, endpoint);
            redisTemplate.opsForValue().set("model:endpoint:" + modelId, 
                                          endpoint, Duration.ofMinutes(5));
            log.info("Registered model: {} with endpoint: {}", modelId, endpoint);
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }
    
    /**
     * 모델 등록 해제
     */
    public Mono<Void> unregisterModel(String modelId) {
        return Mono.fromRunnable(() -> {
            modelEndpoints.remove(modelId);
            redisTemplate.delete("model:endpoint:" + modelId);
            log.info("Unregistered model: {}", modelId);
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }
}