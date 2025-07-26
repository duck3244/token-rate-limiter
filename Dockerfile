# Dockerfile for Simple Token Limiter
FROM openjdk:17-jre-slim

LABEL maintainer="developer@example.com"
LABEL description="Simple Token Rate Limiter for Llama 3.2 1B"

# 작업 디렉토리 설정
WORKDIR /app

# 필요한 패키지 설치
RUN apt-get update && apt-get install -y \
    curl \
    && rm -rf /var/lib/apt/lists/*

# 애플리케이션 JAR 파일 복사
COPY build/libs/simple-token-limiter-*.jar app.jar

# 로그 디렉토리 생성
RUN mkdir -p /app/logs

# 포트 노출
EXPOSE 8080

# 헬스체크 설정
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# JVM 옵션 설정
ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC -XX:+UseContainerSupport"

# 애플리케이션 실행
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]