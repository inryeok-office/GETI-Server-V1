# 공통 Web 및 API 기반

GETI-Server는 실제 Domain API를 구현하기 전에, 모든 HTTP API가 동일한 성공/오류 응답, Validation, Pagination, CORS, Health 규칙을 따르도록 공통 Web 기반을 구성했다. 이 문서는 그 기반의 실제 구현과 정책을 다룬다. 실제 GETI Domain Entity/Controller/API는 이 PR 범위가 아니다.

## Package 위치

공통 Web 기반은 Root Package 바로 아래 `team.inreok.getiserver.web`에 있다. `configuration`/`infrastructure`/`support`([`modularity.md`](../architecture/modularity.md) 참고)와 동일하게 Spring Modulith가 자동 탐지하는 기술 기반 Application Module이다. 이 Package는 여러 Domain Controller가 공유하는 "형식과 변환 규칙"만 담고, 실제 API Endpoint(`@RequestMapping` 등)를 정의하지 않는다. 실제 Domain Controller와 요청/응답 DTO는 각 Domain Module 내부에 위치해야 한다.

```text
team.inreok.getiserver.web
├── ApiResponse.kt            공통 성공 응답 Wrapper
├── PageResponse.kt           Pagination 응답(PageMeta 포함)
├── ErrorCode.kt               Framework Error Code
├── ErrorResponse.kt           공통 오류 응답(FieldErrorResponse 포함)
├── GlobalExceptionHandler.kt  전역 예외 처리
├── CorsProperties.kt          CORS ConfigurationProperties
└── WebCorsConfig.kt           CORS 등록(WebMvcConfigurer)
```

## Dependency

| Dependency | Scope | 역할 |
| --- | --- | --- |
| `spring-boot-starter-webmvc` | `implementation` | Spring MVC(Servlet 기반). 이미 PR 6에서 추가되어 있었다 |
| `spring-boot-starter-validation` | `implementation` | Bean Validation(Hibernate Validator, `jakarta.validation`) |
| `spring-boot-starter-actuator` | `implementation` | Health Endpoint |

이 프로젝트는 Jackson 3.x(`tools.jackson`, PR 6에서 이미 `jackson-module-kotlin` 도입)를 사용한다. Jackson 3.x는 `WRITE_DATES_AS_TIMESTAMPS`가 기본적으로 비활성화되어 있어(Instant/LocalDate 등 `java.time` 타입을 ISO-8601로 직렬화) 별도 `ObjectMapper` Customizer 없이 [`날짜와 시간`](#날짜와-시간) 정책을 만족한다. 이는 [`GlobalExceptionHandlerTest`](../../src/test/kotlin/team/inreok/getiserver/web/GlobalExceptionHandlerTest.kt)의 `timestamp` Field 검증으로 실측 확인했다.

## 성공 응답

```json
{
  "data": { "id": 1 }
}
```

`ApiResponse<T>.of(data)`로 생성한다. `success`/`status`/`message` 같은 중복 필드는 넣지 않는다(HTTP Status로 이미 표현됨). HTTP 204 응답에는 Wrapper를 사용하지 않는다(Body가 없는 응답이므로 Controller가 `ResponseEntity<Void>` 등으로 직접 반환한다).

## 오류 응답

```json
{
  "code": "VALIDATION_FAILED",
  "message": "요청 값 검증에 실패했습니다.",
  "status": 400,
  "path": "/api/v1/example",
  "timestamp": "2026-07-29T07:00:00.123Z",
  "fieldErrors": [
    { "field": "name", "reason": "name은 필수입니다." }
  ]
}
```

`rejectedValue`는 포함하지 않는다. 민감한 요청 값(Password, Token 등)이 Field Error에 그대로 노출될 위험이 있어 기본적으로 제외했다. `requestId`/`traceId`는 아직 요청 추적 Infra가 없어 가짜 값을 만들지 않고 보류했다(Observability PR에서 재검토).

## Error Code

`ErrorCode` Enum은 Framework 수준에서 실제로 처리하는 오류만 정의한다. `USER_NOT_FOUND`처럼 아직 존재하지 않는 Domain Error Code는 추가하지 않았다.

| Error Code | HTTP Status | 발생 상황 |
| --- | --- | --- |
| `INVALID_REQUEST` | 400 | 위 목록에 없는 일반 4xx 오류(Fallback) |
| `VALIDATION_FAILED` | 400 | `@Valid`/`@Validated` Bean Validation 실패(`MethodArgumentNotValidException`, `BindException`, `ConstraintViolationException`) |
| `MALFORMED_JSON` | 400 | 요청 본문 JSON Parsing 실패(`HttpMessageNotReadableException`) |
| `METHOD_NOT_ALLOWED` | 405 | 지원하지 않는 HTTP Method(`HttpRequestMethodNotSupportedException`) |
| `UNSUPPORTED_MEDIA_TYPE` | 415 | 지원하지 않는 Content-Type(`HttpMediaTypeNotSupportedException`) |
| `MISSING_REQUEST_PARAMETER` | 400 | 필수 Query Parameter 누락(`MissingServletRequestParameterException`) |
| `TYPE_MISMATCH` | 400 | 요청 값 형식 오류(`TypeMismatchException`, `MethodArgumentTypeMismatchException` 포함) |
| `RESOURCE_NOT_FOUND` | 404 | 매핑되지 않은 경로(`NoResourceFoundException`) |
| `INTERNAL_SERVER_ERROR` | 500 | 위 목록에 없는 예상하지 못한 예외(Fallback) |

Domain Error Code(예: `USER_NOT_FOUND`)는 실제 Domain 기능 PR에서 해당 Domain Module 내부에 정의하고, 그 Domain의 Web Adapter(Controller 또는 전용 `@ExceptionHandler`)가 이 `ErrorResponse` 형식으로 변환한다.

## 전역 예외 처리

`GlobalExceptionHandler`는 Spring MVC의 `ResponseEntityExceptionHandler`를 상속한다. 이 기반 Class는 Validation, Malformed JSON, 405/415, 404(`NoResourceFoundException`) 등 20종의 표준 MVC 예외를 이미 `handleException(...)`(`final`, `@ExceptionHandler`)으로 잡아 `handleExceptionInternal(...)`에 위임하도록 구현되어 있다(Spring Framework 7.0.8 실제 Bytecode로 확인). `GlobalExceptionHandler`는 이 위임 지점 하나만 재정의해 모든 표준 예외를 공통 `ErrorResponse`로 변환한다.

`ResponseEntityExceptionHandler`가 다루지 않는 예외는 별도 `@ExceptionHandler`를 추가하고 동일한 `handleExceptionInternal`로 위임한다.

- `BindException`(단독 사용 시) → `VALIDATION_FAILED`
- `ConstraintViolationException`(`jakarta.validation`) → `VALIDATION_FAILED`
- 그 외 모든 `Exception`(Fallback) → `INTERNAL_SERVER_ERROR`

`MethodArgumentNotValidException`은 `BindException`의 하위 타입이지만, Spring이 예외 Class 계층 기준으로 더 구체적인 Handler(상속받은 `handleException`)를 우선 선택하므로 두 Handler가 충돌하지 않는다(`ExceptionHandlerMethodResolver`의 표준 동작, 실제 Test로 검증).

### Logging 정책

- 4xx: `logger.warn(...)`, Stack Trace 없이 Path/Status/Exception 이름만 기록
- 5xx: `logger.error(...)`, Stack Trace 포함
- Request Body, Authorization Header, Password/Token 값은 Log에 남기지 않는다.

### 내부 정보 미노출

`handleExceptionInternal`은 항상 `ErrorCode.defaultMessage`(고정 문구)를 사용하고 예외의 실제 `message`를 Client 응답에 포함하지 않는다. Stack Trace, Exception Class 이름도 응답 Body에 없다. [`GlobalExceptionHandlerTest`](../../src/test/kotlin/team/inreok/getiserver/web/GlobalExceptionHandlerTest.kt)의 "예상하지 못한 오류는 500으로 처리되며 내부 정보를 노출하지 않는다" Test로 검증했다.

## Pagination

Spring Data의 `Page<T>`를 API 응답으로 직접 반환하지 않는다. `PageResponse.of(page)`로 변환한다.

```json
{
  "data": ["..."],
  "meta": {
    "page": 0,
    "size": 20,
    "totalElements": 42,
    "totalPages": 3,
    "hasNext": true,
    "hasPrevious": false
  }
}
```

`page`는 Spring Data와 동일하게 0-based를 유지한다(내부 Repository 조회와 외부 API 응답 사이의 별도 변환이 없어 실수 위험이 적다). 최대 Page Size 제한은 실제 API가 생기고 Validation을 붙이는 시점에 해당 Domain PR에서 결정한다(이번 PR에서 임의로 20/100 등의 값을 강제하지 않았다).

## 날짜와 시간

- 서버가 응답하는 시각 값은 `Instant`를 사용하고 UTC ISO-8601(`Z` Suffix)로 직렬화한다. `ErrorResponse.timestamp`가 실제 예시다.
- `LocalDate`는 시간대가 없는 날짜(생년월일 등)에만 사용한다. 이번 PR에는 실제 사용 Field가 없다.
- `LocalDateTime`은 시간대 없는 지역 시간에 실제 의미가 있을 때만 사용하며, 근거 없이 서버 기록 시각 대신 쓰지 않는다.
- 위 정책은 Jackson 3.x의 기본 동작(ISO-8601, Timestamp 직렬화 비활성화)을 그대로 따른 것이며, 별도 `ObjectMapper` Customizer를 추가하지 않았다.

## CORS

`CorsProperties`(`app.web.cors.*`)가 비어 있으면(`allowedOrigins` 기본값 `[]`) CORS Mapping을 등록하지 않는다. 이는 "모든 Origin 허용"이 아니라 "CORS 비활성화"를 의미하며, Browser의 기본 Same-Origin 정책만 적용된다.

| 환경 변수 | Spring Property | 기본값 | 설명 |
| --- | --- | --- | --- |
| `APP_WEB_CORS_ALLOWED_ORIGINS` | `app.web.cors.allowed-origins` | 없음(빈 목록) | 허용할 Origin 목록(Comma-separated) |
| `APP_WEB_CORS_ALLOWED_METHODS` | `app.web.cors.allowed-methods` | `GET,POST,PUT,PATCH,DELETE` | 허용할 HTTP Method |
| `APP_WEB_CORS_ALLOWED_HEADERS` | `app.web.cors.allowed-headers` | `*` | 허용할 Request Header |
| `APP_WEB_CORS_ALLOW_CREDENTIALS` | `app.web.cors.allow-credentials` | `false` | Credential(Cookie 등) 허용 여부 |

`allowCredentials=true`이면서 `allowedOrigins`에 `*`를 포함하면 `CorsProperties` 생성 시점(Application 시작 시점)에 즉시 실패한다(Fail-Fast). 실제 Web Client Origin이 아직 확정되지 않아 `local`/`prod` Profile 어디에도 가짜 운영 Domain을 기본값으로 넣지 않았다. Origin이 확정되면 `application-{profile}.yaml` 또는 배포 환경 변수로 채운다.

## Actuator / Health

`management.endpoints.web.exposure.include=health`, `management.endpoint.health.show-details=never`를 공통 설정에 명시했다(Spring Boot 기본값과 동일하지만, 향후 실수로 다른 Endpoint가 노출되는 것을 막기 위해 명시적으로 선언했다). `env`/`beans`/`configprops`/`heapdump`/`threaddump`/`loggers`/`metrics` 등은 노출하지 않는다.

`GET /actuator/health`는 인증이 아직 없는 현재 단계에서 `{"status":"UP"}` 수준의 최소 정보만 반환한다(`show-details=never`). DataSource/Redis Health Indicator는 Spring Boot가 각각 `DataSource`/`RedisConnectionFactory` Bean 존재 시 자동 등록하며, `docker compose --profile app`으로 실제 PostgreSQL/Redis와 함께 기동해 `UP` 응답을 확인했다([Docker app Profile 검증](#docker-연계) 참고). MinIO는 아직 Client 연동이 없어 Custom Health Indicator를 추가하지 않았다.

## Docker 연계

`compose.yaml`의 `app` Service에 Actuator 기반 Health Check를 추가했다.

```yaml
healthcheck:
  test: ["CMD", "wget", "--no-verbose", "--tries=1", "--spider", "http://localhost:${SERVER_PORT:-8080}/actuator/health"]
```

Runtime Image(`eclipse-temurin:25.0.3_9-jre-alpine`)에는 `curl`이 없고 Alpine 기본 제공 `wget`(BusyBox)만 있어 `wget --spider`를 사용했다(실제 Image에서 `which curl`/`which wget`으로 확인). `docker compose --profile app up -d --build` 실행 후 `docker compose ps`로 `app` Service가 `healthy`가 되는 것을 확인했다.

## OpenAPI

이번 PR에서는 springdoc(OpenAPI) 도입을 보류했다. 현재 저장소에는 Test 전용 Controller 외의 실제 API Endpoint가 하나도 없어, 지금 시점에 OpenAPI UI/JSON을 추가해도 문서화할 실제 API가 없다. 첫 실제 Domain Controller가 추가되는 PR에서 `springdoc-openapi-starter-webmvc-ui`(Spring Boot 4.1/Jackson 3.x와 호환되는 Version을 그 시점에 다시 확인) 도입 여부를 재검토한다.

## Validation

- Request DTO의 `@field:` Bean Validation Annotation으로 요청 형식을 검증한다(Domain 규칙 검증과는 별개).
- `@Valid @RequestBody` 실패는 `MethodArgumentNotValidException`, `ConstraintViolationException`(Path/Query Parameter에 `@Validated` 사용 시)도 동일하게 `VALIDATION_FAILED`로 처리된다.
- Field Error는 실패한 모든 Field를 반환한다(Spring의 `BindingResult.fieldErrors`가 이미 전체 목록을 제공하므로 별도 축약을 하지 않았다).

## Controller 원칙

- Controller는 해당 Domain Module 내부에 작성한다. Root Package에 공용 `controller` Package를 만들지 않는다.
- Controller에 비즈니스 로직을 작성하지 않고, Repository를 직접 호출하지 않으며, Transaction을 시작하지 않는다.
- JPA Entity를 API Response로 직접 반환하지 않는다. 요청 DTO와 응답 DTO를 분리한다.
- `Map<String, Any>`를 응답으로 사용하지 않고 `ApiResponse`/`PageResponse`를 사용한다.
- 새 API를 구현하면 Web Slice Test(`@WebMvcTest`)와 오류 Contract Test를 함께 작성한다.

## Test

| Test | 대상 | 방식 |
| --- | --- | --- |
| [`GlobalExceptionHandlerTest`](../../src/test/kotlin/team/inreok/getiserver/web/GlobalExceptionHandlerTest.kt) | 성공/오류 응답, Pagination, Validation, Malformed JSON, 405, 415, Type Mismatch, 필수 Parameter 누락, 404, 500 | `@WebMvcTest` + `MockMvc` |
| [`WebCorsConfigTest`](../../src/test/kotlin/team/inreok/getiserver/web/WebCorsConfigTest.kt) | CORS 허용/비허용 Origin Preflight | `@WebMvcTest` + `@TestPropertySource` |

두 Test 모두 `WebTestSupportController`(`src/test`에만 존재)를 대상으로 한다. Production Source에는 예시 Controller를 두지 않았다. `WebTestSupportController`와 두 Test Class는 모두 `src/test` Source Set에 있어 `main` Classpath에 포함되지 않고 Spring Modulith Production Module 탐지 대상이 아니다.

## 검증 명령

```bash
./gradlew spotlessCheck
./gradlew detekt
./gradlew test
./gradlew test --tests "*ModularityTest"
./gradlew test --tests "*GlobalExceptionHandlerTest*" --tests "*WebCorsConfigTest*"
./gradlew koverHtmlReport
./gradlew koverXmlReport
./gradlew check
./gradlew clean test build
```

Windows에서는 `.\gradlew.bat`를 사용한다.

## 이번 범위가 아닌 것

- 실제 GETI Domain Controller/API, Domain Error Code
- Spring Security, OAuth, JWT, 인증/인가
- 파일 업로드, WebSocket, SSE
- OpenAPI(springdoc) 실제 도입(첫 Domain Controller PR에서 재검토)
- Request ID/Trace ID, 전체 Observability
- REST Docs, Contract Test
- GitHub Actions CI, CD
