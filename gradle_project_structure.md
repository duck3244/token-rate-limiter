# Token Rate Limiter - 프로젝트 구조

```
token-rate-limiter/
│
├── .gradle/                           # Gradle 캐시 디렉토리
├── .git/                              # Git 버전 관리
├── build/                             # 빌드 출력 디렉토리
│   ├── classes/
│   ├── libs/
│   ├── reports/
│   └── tmp/
│
├── gradle/                            # Gradle Wrapper 파일들
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
│
├── src/                               # 소스 코드 디렉토리
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── example/
│   │   │           └── tokenratelimiter/
│   │   │               ├── TokenRateLimiterApplication.java    # 메인 애플리케이션
│   │   │               │
│   │   │               ├── config/                             # 설정 클래스들
│   │   │               │   ├── RedisConfig.java
│   │   │               │   ├── TokenRateLimitConfig.java
│   │   │               │   ├── ModelProxyRouter.java
│   │   │               │   └── WebConfig.java
│   │   │               │
│   │   │               ├── controller/                         # REST 컨트롤러
│   │   │               │   └── TokenUsageController.java
│   │   │               │
│   │   │               ├── exception/                          # 예외 클래스들
│   │   │               │   ├── GlobalExceptionHandler.java
│   │   │               │   ├── ModelNotFoundException.java
│   │   │               │   ├── TokenRateLimitExceededException.java
│   │   │               │   ├── ModelServiceUnavailableException.java
│   │   │               │   └── InvalidTokenRequestException.java
│   │   │               │
│   │   │               ├── filter/                             # WebFlux 필터
│   │   │               │   └── TokenRateLimitFilter.java
│   │   │               │
│   │   │               ├── metrics/                            # 메트릭 관련
│   │   │               │   └── TokenUsageMetrics.java
│   │   │               │
│   │   │               ├── model/                              # 데이터 모델
│   │   │               │   └── TokenUsage.java
│   │   │               │
│   │   │               ├── service/                            # 비즈니스 로직
│   │   │               │   ├── TokenRateLimitService.java
│   │   │               │   ├── ModelProxyService.java
│   │   │               │   └── ModelRegistryService.java
│   │   │               │
│   │   │               └── task/                               # 스케줄링 작업
│   │   │                   └── TokenUsageScheduledTasks.java
│   │   │
│   │   └── resources/                                          # 리소스 파일들
│   │       ├── application.yml                                 # 메인 설정
│   │       ├── application-dev.yml                             # 개발 환경 설정
│   │       ├── application-prod.yml                            # 프로덕션 환경 설정
│   │       ├── application-docker.yml                          # Docker 환경 설정
│   │       ├── logback-spring.xml                              # 로깅 설정
│   │       └── static/                                         # 정적 파일 (필요시)
│   │
│   └── test/                          # 테스트 코드
│       ├── java/
│       │   └── com/
│       │       └── example/
│       │           └── tokenratelimiter/
│       │               ├── TokenRateLimiterApplicationTests.java
│       │               │
│       │               ├── config/                             # 설정 테스트
│       │               │   └── TestConfiguration.java
│       │               │
│       │               ├── controller/                         # 컨트롤러 테스트
│       │               │   └── TokenUsageControllerTest.java
│       │               │
│       │               ├── integration/                        # 통합 테스트
│       │               │   ├── TokenRateLimitIntegrationTest.java
│       │               │   ├── RedisIntegrationTest.java
│       │               │   └── ModelProxyIntegrationTest.java
│       │               │
│       │               ├── service/                            # 서비스 테스트
│       │               │   ├── TokenRateLimitServiceTest.java
│       │               │   ├── ModelProxyServiceTest.java
│       │               │   └── ModelRegistryServiceTest.java
│       │               │
│       │               └── util/                               # 테스트 유틸리티
│       │                   ├── TestContainerConfig.java
│       │                   └── MockDataFactory.java
│       │
│       └── resources/                                          # 테스트 리소스
│           ├── application-test.yml                            # 테스트 설정
│           └── test-data/                                      # 테스트 데이터
│               ├── sample-requests.json
│               └── mock-responses.json
│
├── k8s/                               # Kubernetes 매니페스트
│   ├── namespace.yaml
│   ├── redis/
│   │   ├── redis-deployment.yaml
│   │   ├── redis-service.yaml
│   │   └── redis-configmap.yaml
│   ├── token-rate-limiter/
│   │   ├── deployment.yaml
│   │   ├── service.yaml
│   │   ├── configmap.yaml
│   │   ├── secret.yaml
│   │   ├── hpa.yaml
│   │   └── pdb.yaml
│   ├── vllm/
│   │   ├── llama2-7b-deployment.yaml
│   │   ├── llama2-13b-deployment.yaml
│   │   └── model-services.yaml
│   ├── monitoring/
│   │   ├── prometheus/
│   │   │   ├── prometheus-deployment.yaml
│   │   │   ├── prometheus-service.yaml
│   │   │   └── prometheus-configmap.yaml
│   │   ├── grafana/
│   │   │   ├── grafana-deployment.yaml
│   │   │   ├── grafana-service.yaml
│   │   │   └── dashboard-configmap.yaml
│   │   └── servicemonitor.yaml
│   ├── ingress.yaml
│   └── networkpolicy.yaml
│
├── docker/                            # Docker 관련 파일
│   ├── Dockerfile
│   ├── Dockerfile.gradle
│   ├── Dockerfile.dev
│   ├── docker-compose.yml
│   ├── docker-compose-gradle.yml
│   └── docker-compose.dev.yml
│
├── monitoring/                        # 모니터링 설정
│   ├── prometheus/
│   │   ├── prometheus.yml
│   │   ├── alert-rules.yml
│   │   └── targets.yml
│   ├── grafana/
│   │   ├── dashboards/
│   │   │   ├── token-usage-dashboard.json
│   │   │   ├── system-overview-dashboard.json
│   │   │   └── error-monitoring-dashboard.json
│   │   └── datasources/
│   │       └── prometheus-datasource.yml
│   └── alertmanager/
│       ├── alertmanager.yml
│       └── notification-templates/
│
├── scripts/                           # 유틸리티 스크립트
│   ├── gradle-scripts.sh              # Gradle 편의 스크립트
│   ├── setup-dev-env.sh               # 개발 환경 설정
│   ├── deploy-k8s.sh                  # Kubernetes 배포
│   ├── load-test.sh                   # 부하 테스트
│   └── backup-redis.sh                # Redis 백업
│
├── docs/                              # 문서
│   ├── api/                           # API 문서
│   │   ├── openapi.yaml
│   │   └── postman-collection.json
│   ├── architecture/                  # 아키텍처 문서
│   │   ├── system-design.md
│   │   ├── database-schema.md
│   │   └── security-guide.md
│   ├── deployment/                    # 배포 가이드
│   │   ├── kubernetes-deployment.md
│   │   ├── docker-deployment.md
│   │   └── production-checklist.md
│   └── development/                   # 개발 가이드
│       ├── coding-standards.md
│       ├── testing-guide.md
│       └── contribution-guide.md
│
├── performance/                       # 성능 테스트
│   ├── jmeter/
│   │   ├── load-test.jmx
│   │   ├── stress-test.jmx
│   │   └── spike-test.jmx
│   ├── k6/
│   │   ├── load-test.js
│   │   └── stress-test.js
│   └── results/                       # 테스트 결과
│
├── logs/                              # 로그 파일 (gitignore)
├── .gitignore                         # Git 무시 파일
├── .gitattributes                     # Git 속성
├── .dockerignore                      # Docker 무시 파일
│
├── build.gradle                       # Gradle 빌드 스크립트
├── settings.gradle                    # Gradle 설정
├── gradle.properties                  # Gradle 속성
├── gradlew                            # Gradle Wrapper (Unix)
├── gradlew.bat                        # Gradle Wrapper (Windows)
│
├── README.md                          # 프로젝트 설명서
├── CHANGELOG.md                       # 변경 이력
├── LICENSE                            # 라이선스
├── CONTRIBUTING.md                    # 기여 가이드
└── .github/                           # GitHub 설정
    ├── workflows/                     # GitHub Actions
    │   ├── ci.yml                     # 지속적 통합
    │   ├── cd.yml                     # 지속적 배포
    │   ├── security-scan.yml          # 보안 스캔
    │   └── dependency-check.yml       # 의존성 검사
    ├── ISSUE_TEMPLATE/                # 이슈 템플릿
    │   ├── bug_report.md
    │   ├── feature_request.md
    │   └── question.md
    └── PULL_REQUEST_TEMPLATE.md       # PR 템플릿
```

## 📁 주요 디렉토리 설명

### `/src/main/java` - 메인 소스코드
- **config/**: Spring Boot 설정 클래스들
- **controller/**: REST API 엔드포인트
- **service/**: 핵심 비즈니스 로직
- **filter/**: WebFlux 필터
- **exception/**: 예외 처리
- **metrics/**: 모니터링 메트릭
- **model/**: 데이터 모델
- **task/**: 스케줄링 작업

### `/src/test` - 테스트 코드
- **integration/**: 통합 테스트
- **service/**: 단위 테스트
- **util/**: 테스트 유틸리티

### `/k8s` - Kubernetes 매니페스트
- 각 컴포넌트별 배포 설정
- 모니터링 스택 설정
- 네트워크 정책

### `/docker` - Docker 설정
- 다양한 환경별 Dockerfile
- Docker Compose 설정

### `/monitoring` - 모니터링 설정
- Prometheus, Grafana 설정
- 대시보드와 알람 규칙

### `/scripts` - 유틸리티 스크립트
- 빌드, 배포, 테스트 자동화
- 개발 환경 설정

### `/docs` - 프로젝트 문서
- API 문서, 아키텍처 가이드
- 배포 및 개발 가이드