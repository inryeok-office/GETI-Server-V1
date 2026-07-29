# syntax=docker/dockerfile:1
#
# 로컬 전체 Container 환경 검증용 Image다 (compose.yaml의 `app` Profile 전용).
# 운영 배포용 Image 전략은 별도로 확정하지 않았다.

FROM eclipse-temurin:25.0.3_9-jdk-alpine AS build
WORKDIR /workspace

COPY gradlew gradlew.bat ./
COPY gradle gradle
COPY settings.gradle.kts build.gradle.kts ./
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null

COPY config config
COPY src src
RUN ./gradlew --no-daemon bootJar

FROM eclipse-temurin:25.0.3_9-jre-alpine AS runtime
WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring
COPY --from=build /workspace/build/libs/*.jar app.jar
RUN chown spring:spring app.jar
USER spring

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
