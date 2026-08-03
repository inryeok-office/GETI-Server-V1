# syntax=docker/dockerfile:1
#
# 원래 로컬 전체 Container 환경 검증용 Image였다(compose.yaml의 `app` Profile 전용). 실제로는
# .github/workflows/cd.yml이 develop 배포마다 EC2 서버에서 이 Image를 그대로 Build/실행한다
# (docs/development/docker.md 참고). Registry(GHCR/ECR)를 사용하는 별도 운영 Image 전략은
# 아직 확정되지 않았다.

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

# /actuator/info의 deployment 필드(DeploymentInfoProperties)에 반영된다. ARG는 이 Stage에서
# 다시 선언해야 Build 시점에 값을 받고, ENV로 재선언해야 Container Runtime에도 남는다(ARG만으로는
# Container 실행 시점에 사라진다). APP_ENVIRONMENT도 Runtime Environment로 별도 전달하지 않고
# 여기서 Build Argument로만 받는다 — CD가 `sudo docker compose ...` 명령 자체를 바꾸지 않고
# `--build-arg`만으로 네 값을 모두 전달할 수 있게 하기 위함이다(sudoers가 docker 실행만 허용하는
# 환경에서도 안전하게 동작, docs/development/cd.md 참고).
ARG APP_VERSION=0.0.1-SNAPSHOT
ARG APP_GIT_SHA=unknown
ARG APP_BUILD_TIME=unknown
ARG APP_ENVIRONMENT=local
ENV APP_VERSION=${APP_VERSION} \
    APP_GIT_SHA=${APP_GIT_SHA} \
    APP_BUILD_TIME=${APP_BUILD_TIME} \
    APP_ENVIRONMENT=${APP_ENVIRONMENT}

RUN addgroup -S spring && adduser -S spring -G spring
COPY --from=build /workspace/build/libs/*.jar app.jar
RUN chown spring:spring app.jar
USER spring

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
