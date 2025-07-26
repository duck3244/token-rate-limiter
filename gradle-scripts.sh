#!/bin/bash

# Gradle 유틸리티 스크립트 모음

set -e

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 로그 함수
log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 도움말
show_help() {
    cat << EOF
Gradle 유틸리티 스크립트

사용법: $0 [COMMAND] [OPTIONS]

Commands:
    build           애플리케이션 빌드
    test            테스트 실행
    run-dev         개발 환경에서 실행
    run-prod        프로덕션 환경에서 실행
    docker-build    Docker 이미지 빌드
    docker-run      Docker 컨테이너 실행
    clean           빌드 아티팩트 정리
    check-deps      의존성 취약점 검사
    format          코드 포맷팅
    help            이 도움말 표시

Options:
    --skip-tests    테스트 건너뛰기
    --debug         디버그 모드
    --profile       특정 프로파일 사용

Examples:
    $0 build --skip-tests
    $0 run-dev --debug
    $0 docker-build
    $0 test --profile=integration

EOF
}

# 빌드 함수
build_app() {
    local skip_tests=""
    
    if [[ "$1" == "--skip-tests" ]]; then
        skip_tests="-x test"
        log_warn "테스트를 건너뜁니다"
    fi
    
    log_info "애플리케이션을 빌드합니다..."
    ./gradlew clean build $skip_tests
    
    if [ $? -eq 0 ]; then
        log_info "빌드가 성공적으로 완료되었습니다"
    else
        log_error "빌드 실패"
        exit 1
    fi
}

# 테스트 함수
run_tests() {
    local profile="$1"
    
    log_info "테스트를 실행합니다..."
    
    if [[ -n "$profile" ]]; then
        ./gradlew test -Dspring.profiles.active=$profile
    else
        ./gradlew test
    fi
    
    if [ $? -eq 0 ]; then
        log_info "모든 테스트가 성공했습니다"
        
        # 테스트 리포트 열기 (macOS의 경우)
        if [[ "$OSTYPE" == "darwin"* ]]; then
            open build/reports/tests/test/index.html
        fi
    else
        log_error "테스트 실패"
        exit 1
    fi
}

# 개발 환경 실행
run_dev() {
    local debug=""
    
    if [[ "$1" == "--debug" ]]; then
        debug="-Dspring-boot.run.jvmArguments='-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005'"
        log_info "디버그 모드로 실행합니다 (포트: 5005)"
    fi
    
    log_info "개발 환경에서 애플리케이션을 실행합니다..."
    ./gradlew bootRun $debug --args='--spring.profiles.active=dev'
}

# 프로덕션 환경 실행
run_prod() {
    log_info "프로덕션 환경에서 애플리케이션을 실행합니다..."
    ./gradlew bootRun --args='--spring.profiles.active=prod'
}

# Docker 이미지 빌드
docker_build() {
    log_info "Docker 이미지를 빌드합니다..."
    
    # 먼저 애플리케이션 빌드
    ./gradlew clean bootJar
    
    if [ $? -eq 0 ]; then
        # Docker 이미지 빌드
        docker build -f Dockerfile.gradle -t token-rate-limiter:latest .
        
        if [ $? -eq 0 ]; then
            log_info "Docker 이미지 빌드가 완료되었습니다"
            docker images | grep token-rate-limiter
        else
            log_error "Docker 이미지 빌드 실패"
            exit 1
        fi
    else
        log_error "애플리케이션 빌드 실패"
        exit 1
    fi
}

# Docker 컨테이너 실행
docker_run() {
    log_info "Docker 컨테이너를 실행합니다..."
    
    # Redis 컨테이너가 실행 중인지 확인
    if ! docker ps | grep -q redis; then
        log_warn "Redis 컨테이너를 시작합니다..."
        docker run -d --name redis -p 6379:6379 redis:7-alpine
    fi
    
    # 애플리케이션 컨테이너 실행
    docker run -d \
        --name token-rate-limiter \
        -p 8080:8080 \
        -e REDIS_HOST=redis \
        -e SPRING_PROFILES_ACTIVE=docker \
        --link redis:redis \
        token-rate-limiter:latest
    
    if [ $? -eq 0 ]; then
        log_info "컨테이너가 성공적으로 시작되었습니다"
        log_info "애플리케이션: http://localhost:8080"
        log_info "헬스체크: http://localhost:8080/actuator/health"
    else
        log_error "컨테이너 시작 실패"
        exit 1
    fi
}

# 정리 함수
clean_all() {
    log_info "빌드 아티팩트를 정리합니다..."
    
    ./gradlew clean
    
    # Docker 이미지 정리 (선택사항)
    read -p "Docker 이미지도 삭제하시겠습니까? (y/N): " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        docker rmi token-rate-limiter:latest 2>/dev/null || true
        log_info "Docker 이미지도 삭제되었습니다"
    fi
    
    log_info "정리가 완료되었습니다"
}

# 의존성 취약점 검사
check_dependencies() {
    log_info "의존성 취약점을 검사합니다..."
    
    ./gradlew dependencyCheckAnalyze
    
    if [ $? -eq 0 ]; then
        log_info "의존성 검사가 완료되었습니다"
        
        # 리포트 열기 (macOS의 경우)
        if [[ "$OSTYPE" == "darwin"* ]]; then
            open build/reports/dependency-check-report.html
        fi
    else
        log_error "의존성 검사 실패"
        exit 1
    fi
}

# 코드 포맷팅
format_code() {
    log_info "코드를 포맷팅합니다..."
    
    # Spotless 플러그인이 있다면
    if ./gradlew tasks | grep -q spotlessApply; then
        ./gradlew spotlessApply
    else
        log_warn "코드 포맷팅 플러그인이 설정되지 않았습니다"
        log_info "build.gradle에 다음을 추가하세요:"
        echo "plugins {"
        echo "    id 'com.diffplug.spotless' version '6.23.3'"
        echo "}"
    fi
}

# 개발 환경 설정
setup_dev_env() {
    log_info "개발 환경을 설정합니다..."
    
    # Git hooks 설정
    if [ -d ".git" ]; then
        cat > .git/hooks/pre-commit << 'EOF'
#!/bin/sh
echo "Running tests before commit..."
./gradlew test
EOF
        chmod +x .git/hooks/pre-commit
        log_info "Git pre-commit hook이 설정되었습니다"
    fi
    
    # IDE 설정 생성
    ./gradlew idea eclipse
    
    log_info "개발 환경 설정이 완료되었습니다"
}

# 성능 테스트
performance_test() {
    log_info "성능 테스트를 실행합니다..."
    
    # JMeter 스크립트가 있다면
    if [ -f "performance/load-test.jmx" ]; then
        jmeter -n -t performance/load-test.jmx -l performance/results.jtl
    else
        log_warn "성능 테스트 스크립트를 찾을 수 없습니다"
        log_info "performance/load-test.jmx 파일을 생성하세요"
    fi
}

# 메인 스크립트 로직
main() {
    case "${1:-help}" in
        build)
            build_app "$2"
            ;;
        test)
            run_tests "$2"
            ;;
        run-dev)
            run_dev "$2"
            ;;
        run-prod)
            run_prod
            ;;
        docker-build)
            docker_build
            ;;
        docker-run)
            docker_run
            ;;
        clean)
            clean_all
            ;;
        check-deps)
            check_dependencies
            ;;
        format)
            format_code
            ;;
        setup-dev)
            setup_dev_env
            ;;
        perf-test)
            performance_test
            ;;
        help|--help|-h)
            show_help
            ;;
        *)
            log_error "알 수 없는 명령어: $1"
            show_help
            exit 1
            ;;
    esac
}

# 스크립트 실행
main "$@"