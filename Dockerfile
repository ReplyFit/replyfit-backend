# ---------- 1단계: 빌드 ----------
FROM gradle:8.14.3-jdk21 AS builder
WORKDIR /workspace

# 의존성 캐시 레이어
COPY build.gradle settings.gradle ./
RUN gradle dependencies --no-daemon || true

# 소스 복사 후 빌드
COPY src ./src
RUN gradle bootJar --no-daemon

# ---------- 2단계: 실행 ----------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S replyfit && adduser -S replyfit -G replyfit
USER replyfit

COPY --from=builder /workspace/build/libs/*.jar app.jar

EXPOSE 8080

# SPRING_PROFILES_ACTIVE=api  → REST API 서버
# SPRING_PROFILES_ACTIVE=worker → Kafka 컨슈머 워커
ENV SPRING_PROFILES_ACTIVE=api \
    JAVA_OPTS="-XX:MaxRAMPercentage=75.0"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
