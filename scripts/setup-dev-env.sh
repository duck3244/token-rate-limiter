# scripts/setup-dev-env.sh
#!/bin/bash

set -e

echo "🔧 개발 환경을 설정합니다..."

# 색상 정의
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# Git hooks 설정
if [ -d ".git" ]; then
    echo -e "${YELLOW}Git hooks를 설정합니다...${NC}"
    
    # Pre-commit hook
    cat > .git/hooks/pre-commit << 'EOF'
#!/bin/sh
echo "테스트를 실행합니다..."
./gradlew test
if [ $? -ne 0 ]; then
    echo "테스트 실패로 커밋이 중단되었습니다."
    exit 1
fi
EOF
    chmod +x .git/hooks/pre-commit
    
    # Pre-push hook
    cat > .git/hooks/pre-push << 'EOF'
#!/bin/sh
echo "코드 품질 검사를 실행합니다..."
./gradlew checkQuality
if [ $? -ne 0 ]; then
    echo "코드 품질 검사 실패로 푸시가 중단되었습니다."
    exit 1
fi
EOF
    chmod +x .git/hooks/pre-push
    
    echo -e "${GREEN}Git hooks 설정 완료${NC}"
fi

# IDE 프로젝트 파일 생성
echo -e "${YELLOW}IDE 프로젝트 파일을 생성합니다...${NC}"
./gradlew idea eclipse

# 개발 도구 체크
echo -e "${YELLOW}개발 도구를 확인합니다...${NC}"

# Java 버전 확인
if java -version 2>&1 | grep -q "17"; then
    echo -e "${GREEN}✓ Java 17 설치됨${NC}"
else
    echo -e "${YELLOW}⚠ Java 17이 필요합니다${NC}"
fi

# Docker 확인
if command -v docker &> /dev/null; then
    echo -e "${GREEN}✓ Docker 설치됨${NC}"
else
    echo -e "${YELLOW}⚠ Docker 설치가 필요합니다${NC}"
fi

# kubectl 확인
if command -v kubectl &> /dev/null; then
    echo -e "${GREEN}✓ kubectl 설치됨${NC}"
else
    echo -e "${YELLOW}⚠ kubectl 설치가 필요합니다${NC}"
fi

echo -e "${GREEN}개발 환경 설정이 완료되었습니다! 🎉${NC}"

---

# scripts/deploy-k8s.sh
#!/bin/bash

set -e

echo "🚀 Kubernetes에 배포를 시작합니다..."

# 변수 설정
NAMESPACE=${NAMESPACE:-model-serving}
IMAGE_TAG=${IMAGE_TAG:-latest}
DRY_RUN=${DRY_RUN:-false}

# 색상 정의
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

# 함수 정의
log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# kubectl 설치 확인
if ! command -v kubectl &> /dev/null; then
    log_error "kubectl이 설치되지 않았습니다."
    exit 1
fi

# 클러스터 연결 확인
if ! kubectl cluster-info &> /dev/null; then
    log_error "Kubernetes 클러스터에 연결할 수 없습니다."
    exit 1
fi

# 네임스페이스 생성
log_info "네임스페이스 '$NAMESPACE' 생성 중..."
if $DRY_RUN; then
    kubectl create namespace $NAMESPACE --dry-run=client -o yaml
else
    kubectl create namespace $NAMESPACE --dry-run=client -o yaml | kubectl apply -f -
fi

# Redis 배포
log_info "Redis 배포 중..."
if $DRY_RUN; then
    kubectl apply --dry-run=client -f k8s/redis/ -n $NAMESPACE
else
    kubectl apply -f k8s/redis/ -n $NAMESPACE
fi

# Token Rate Limiter 배포
log_info "Token Rate Limiter 배포 중..."
if $DRY_RUN; then
    kubectl apply --dry-run=client -f k8s/token-rate-limiter/ -n $NAMESPACE
else
    kubectl apply -f k8s/token-rate-limiter/ -n $NAMESPACE
fi

# vLLM 모델 서버 배포
log_info "vLLM 모델 서버 배포 중..."
if $DRY_RUN; then
    kubectl apply --dry-run=client -f k8s/vllm/ -n $NAMESPACE
else
    kubectl apply -f k8s/vllm/ -n $NAMESPACE
fi

# 모니터링 스택 배포
log_info "모니터링 스택 배포 중..."
if $DRY_RUN; then
    kubectl apply --dry-run=client -f k8s/monitoring/ -n monitoring
else
    kubectl create namespace monitoring --dry-run=client -o yaml | kubectl apply -f -
    kubectl apply -f k8s/monitoring/ -n monitoring
fi

# 배포 상태 확인
if ! $DRY_RUN; then
    log_info "배포 상태를 확인합니다..."
    
    # Pod 상태 확인
    kubectl get pods -n $NAMESPACE
    
    # 서비스 확인
    kubectl get svc -n $NAMESPACE
    
    # 배포 대기
    log_info "배포 완료를 기다립니다..."
    kubectl wait --for=condition=ready pod -l app=token-rate-limiter -n $NAMESPACE --timeout=300s
    kubectl wait --for=condition=ready pod -l app=redis -n $NAMESPACE --timeout=300s
    
    log_info "배포가 성공적으로 완료되었습니다! 🎉"
    
    # 접속 정보 표시
    echo ""
    echo "📋 접속 정보:"
    echo "- Token Rate Limiter: kubectl port-forward svc/token-rate-limiter-service 8080:8080 -n $NAMESPACE"
    echo "- Grafana: kubectl port-forward svc/grafana 3000:3000 -n monitoring"
    echo "- Prometheus: kubectl port-forward svc/prometheus 9090:9090 -n monitoring"
fi

---

# scripts/load-test.sh
#!/bin/bash

set -e

echo "📈 부하 테스트를 시작합니다..."

# 변수 설정
TARGET_URL=${TARGET_URL:-http://localhost:8080}
USERS=${USERS:-100}
DURATION=${DURATION:-300}
MODEL_ID=${MODEL_ID:-llama2-7b}

# 색상 정의
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

# JMeter 테스트
if command -v jmeter &> /dev/null; then
    log_info "JMeter로 부하 테스트를 실행합니다..."
    
    # 결과 디렉토리 생성
    mkdir -p performance/results
    
    # JMeter 테스트 실행
    jmeter -n -t performance/jmeter/load-test.jmx \
        -Jusers=$USERS \
        -Jduration=$DURATION \
        -Jtarget_url=$TARGET_URL \
        -Jmodel_id=$MODEL_ID \
        -l performance/results/load-test-$(date +%Y%m%d-%H%M%S).jtl \
        -e -o performance/results/html-report-$(date +%Y%m%d-%H%M%S)
    
    log_info "JMeter 테스트 완료. 결과는 performance/results/ 에서 확인하세요."
fi

# K6 테스트
if command -v k6 &> /dev/null; then
    log_info "K6로 부하 테스트를 실행합니다..."
    
    export TARGET_URL=$TARGET_URL
    export USERS=$USERS
    export DURATION=$DURATION
    export MODEL_ID=$MODEL_ID
    
    k6 run performance/k6/load-test.js
    
    log_info "K6 테스트 완료."
fi

# curl 기반 간단한 테스트
if ! command -v jmeter &> /dev/null && ! command -v k6 &> /dev/null; then
    log_warn "JMeter나 K6가 설치되지 않았습니다. curl로 간단한 테스트를 실행합니다."
    
    log_info "기본 연결 테스트..."
    curl -f $TARGET_URL/actuator/health || exit 1
    
    log_info "토큰 사용량 조회 테스트..."
    curl -f $TARGET_URL/api/v1/admin/token-usage/$MODEL_ID/test-user || exit 1
    
    log_info "간단한 부하 테스트 (10회 요청)..."
    for i in {1..10}; do
        curl -X POST $TARGET_URL/api/v1/models/$MODEL_ID/chat/completions \
            -H "Content-Type: application/json" \
            -H "X-User-ID: test-user-$i" \
            -d '{
                "messages": [{"role": "user", "content": "Hello"}],
                "max_tokens": 10
            }' &
    done
    wait
    
    log_info "기본 테스트 완료."
fi

echo "부하 테스트가 완료되었습니다! 📊"

---

# scripts/backup-redis.sh
#!/bin/bash

set -e

echo "💾 Redis 백업을 시작합니다..."

# 변수 설정
NAMESPACE=${NAMESPACE:-model-serving}
BACKUP_DIR=${BACKUP_DIR:-./backups}
DATE=$(date +%Y%m%d-%H%M%S)
REDIS_POD=$(kubectl get pods -n $NAMESPACE -l app=redis -o jsonpath='{.items[0].metadata.name}')

# 색상 정의
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

# 백업 디렉토리 생성
mkdir -p $BACKUP_DIR

if [ -z "$REDIS_POD" ]; then
    log_warn "Redis Pod를 찾을 수 없습니다."
    exit 1
fi

log_info "Redis Pod: $REDIS_POD"

# Redis 백업 실행
log_info "BGSAVE 명령어를 실행합니다..."
kubectl exec -n $NAMESPACE $REDIS_POD -- redis-cli BGSAVE

# 백업 완료 대기
log_info "백업 완료를 기다립니다..."
while true; do
    LASTSAVE=$(kubectl exec -n $NAMESPACE $REDIS_POD -- redis-cli LASTSAVE)
    sleep 2
    CURRENT=$(kubectl exec -n $NAMESPACE $REDIS_POD -- redis-cli LASTSAVE)
    if [ "$LASTSAVE" != "$CURRENT" ]; then
        break
    fi
done

# 백업 파일 복사
log_info "백업 파일을 복사합니다..."
kubectl cp $NAMESPACE/$REDIS_POD:/data/dump.rdb $BACKUP_DIR/redis-backup-$DATE.rdb

# 백업 파일 압축
log_info "백업 파일을 압축합니다..."
gzip $BACKUP_DIR/redis-backup-$DATE.rdb

# 오래된 백업 파일 삭제 (7일 이상)
log_info "오래된 백업 파일을 정리합니다..."
find $BACKUP_DIR -name "redis-backup-*.rdb.gz" -mtime +7 -delete

log_info "Redis 백업이 완료되었습니다: $BACKUP_DIR/redis-backup-$DATE.rdb.gz"

# 백업 파일 크기 표시
BACKUP_SIZE=$(du -h $BACKUP_DIR/redis-backup-$DATE.rdb.gz | cut -f1)
log_info "백업 파일 크기: $BACKUP_SIZE"

# 현재 Redis 메모리 사용량 표시
MEMORY_USAGE=$(kubectl exec -n $NAMESPACE $REDIS_POD -- redis-cli INFO memory | grep used_memory_human | cut -d: -f2 | tr -d '\r')
log_info "현재 Redis 메모리 사용량: $MEMORY_USAGE"

echo "백업 작업이 완료되었습니다! 💾"