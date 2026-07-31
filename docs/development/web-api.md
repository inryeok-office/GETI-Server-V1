# 공통 Web 및 API 기반

GETI-Server는 실제 Domain API를 구현하기 전에, 모든 HTTP API가 동일한 성공/오류 응답, Validation, Pagination, CORS, Health 규칙을 따르도록 공통 Web 기반을 구성했다. 이 문서는 그 기반의 실제 구현과 정책을 다룬다. 실제 GETI Domain Entity/Controller/API는 이 PR 범위가 아니다.

## Package 위치

공통 Web/오류 기반은 Root Package 바로 아래 `team.inreok.getiserver.global`에 있다. `configuration`/`infrastructure`/`support`([`modularity.md`](../architecture/modularity.md) 참고)와 동일하게 Spring Modulith가 자동 탐지하는 기술 기반 Application Module이다. PR 9에서는 `web` Package 하나로 구성했으나, PR 12에서 사용자가 확정한 `{root}/domain`, `{root}/global` 최상위 구조에 맞춰 "오류 계약"(`global.error`)과 "HTTP 응답/설정"(`global.web`)으로 재구성했다. 이 Package는 여러 Domain Controller가 공유하는 "형식과 변환 규칙"만 담고, 실제 API Endpoint(`@RequestMapping` 등)를 정의하지 않는다. 실제 Domain Controller와 요청/응답 DTO는 각 Domain Module 내부에 위치해야 한다.

```text
team.inreok.getiserver.global
├── error
│   ├── ErrorCode.kt               Framework Error Code
│   ├── ErrorResponse.kt           공통 오류 응답(FieldErrorResponse 포함)
│   ├── BusinessException.kt       Domain 예외 공통 기반
│   └── GlobalExceptionHandler.kt  전역 예외 처리
└── web
    ├── ApiResponse.kt             공통 성공 응답 Wrapper
    ├── PageResponse.kt            Pagination 응답(PageMeta 포함)
    ├── CorsProperties.kt          CORS ConfigurationProperties
    ├── WebCorsConfig.kt           CORS 등록(WebMvcConfigurer)
    ├── WebPageableConfig.kt       Pagination 최대 Size 강제
    └── RequestIdFilter.kt         요청별 requestId 생성/MDC 등록
```

## Dependency

| Dependency | Scope | 역할 |
| --- | --- | --- |
| `spring-boot-starter-webmvc` | `implementation` | Spring MVC(Servlet 기반). 이미 PR 6에서 추가되어 있었다 |
| `spring-boot-starter-validation` | `implementation` | Bean Validation(Hibernate Validator, `jakarta.validation`) |
| `spring-boot-starter-actuator` | `implementation` | Health Endpoint |

이 프로젝트는 Jackson 3.x(`tools.jackson`, PR 6에서 이미 `jackson-module-kotlin` 도입)를 사용한다. Jackson 3.x는 `WRITE_DATES_AS_TIMESTAMPS`가 기본적으로 비활성화되어 있어(Instant/LocalDate 등 `java.time` 타입을 ISO-8601로 직렬화) 별도 `ObjectMapper` Customizer 없이 [`날짜와 시간`](#날짜와-시간) 정책을 만족한다. 이는 [`GlobalExceptionHandlerTest`](../../src/test/kotlin/team/inreok/getiserver/global/error/GlobalExceptionHandlerTest.kt)의 `timestamp` Field 검증으로 실측 확인했다.

## 성공 응답

```json
{
  "success": true,
  "data": { "id": 1 },
  "meta": { "requestId": "3f6e9c2a-1c4b-4b8e-9c7a-1a2b3c4d5e6f" }
}
```

`ApiResponse<T>.of(data)`로 생성한다. GETI Notion API 명세서의 `success`/`data`/`meta.requestId` Wrapper 구조를 따른다(2026-07-31, [`docs/audit/notion-repository-sync.md`](../audit/notion-repository-sync.md)의 "API 공통 응답 Contract" DECISION_REQUIRED를 사용자가 이 구조로 채택하기로 결정). HTTP 204 응답에는 Wrapper를 사용하지 않는다(Body가 없는 응답이므로 Controller가 `ResponseEntity<Void>` 등으로 직접 반환한다).

## 오류 응답

```json
{
  "success": false,
  "error": {
    "code": "VALIDATION_FAILED",
    "message": "요청 값 검증에 실패했습니다.",
    "status": 400,
    "path": "/api/v1/example",
    "timestamp": "2026-07-29T07:00:00.123Z",
    "fieldErrors": [
      { "field": "name", "reason": "name은 필수입니다." }
    ]
  },
  "meta": { "requestId": "3f6e9c2a-1c4b-4b8e-9c7a-1a2b3c4d5e6f" }
}
```

`rejectedValue`는 포함하지 않는다. 민감한 요청 값(Password, Token 등)이 Field Error에 그대로 노출될 위험이 있어 기본적으로 제외했다. `status`/`path`/`timestamp`는 Notion 명세에는 없지만 Log/Trace 연계에 유용해 `error` 객체 하위에 추가 Field로 유지했다(계약을 좁히지 않는 추가 정보). `requestId`는 [`RequestIdFilter`](#requestid)가 생성하며, 성공/오류 응답 모두 `meta.requestId`에 같은 값을 담는다.

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

## 공통 예외 기반 (BusinessException)

`BusinessException(errorCode, message)`은 Domain/Application 계층에서 발생하는 예외의 공통 기반이다. `global.error` Package는 특정 Domain을 알지 못하므로 이 Class 자체는 어떤 Domain 전용 예외도 미리 정의하지 않는다 — 실제 Domain 예외는 각 Domain Module 안에서 `BusinessException`을 상속해 정의하고, 해당 Domain의 `ErrorCode`(또는 `global.error.ErrorCode`의 기존 값)를 넘긴다.

```kotlin
class MemberNotFoundException(
    memberId: Long,
) : BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Member($memberId)를 찾을 수 없습니다.")
```

`GlobalExceptionHandler`는 `BusinessException`을 `ex.errorCode.status`로 응답하고, `ex.message`를 그대로 사용한다. 이 Message는 예외를 던지는 코드가 직접 작성한 안전한 사용자 노출용 문구이므로 [`내부 정보 미노출`](#내부-정보-미노출) 원칙(예상하지 못한 예외의 실제 `message`를 노출하지 않음)과 충돌하지 않는다.

## 전역 예외 처리

`GlobalExceptionHandler`는 Spring MVC의 `ResponseEntityExceptionHandler`를 상속한다. 이 기반 Class는 Validation, Malformed JSON, 405/415, 404(`NoResourceFoundException`) 등 20종의 표준 MVC 예외를 이미 `handleException(...)`(`final`, `@ExceptionHandler`)으로 잡아 `handleExceptionInternal(...)`에 위임하도록 구현되어 있다(Spring Framework 7.0.8 실제 Bytecode로 확인). `GlobalExceptionHandler`는 이 위임 지점 하나만 재정의해 모든 표준 예외를 공통 `ErrorResponse`로 변환한다.

`ResponseEntityExceptionHandler`가 다루지 않는 예외는 별도 `@ExceptionHandler`를 추가하고 동일한 `handleExceptionInternal`로 위임한다.

- `BusinessException` → `ex.errorCode`(Domain/Application이 지정한 상태와 Message)
- `BindException`(단독 사용 시) → `VALIDATION_FAILED`
- `ConstraintViolationException`(`jakarta.validation`) → `VALIDATION_FAILED`
- 그 외 모든 `Exception`(Fallback) → `INTERNAL_SERVER_ERROR`

`MethodArgumentNotValidException`은 `BindException`의 하위 타입이지만, Spring이 예외 Class 계층 기준으로 더 구체적인 Handler(상속받은 `handleException`)를 우선 선택하므로 두 Handler가 충돌하지 않는다(`ExceptionHandlerMethodResolver`의 표준 동작, 실제 Test로 검증).

### Logging 정책

- 4xx: `logger.warn(...)`, Stack Trace 없이 Path/Status/Exception 이름만 기록
- 5xx: `logger.error(...)`, Stack Trace 포함
- Request Body, Authorization Header, Password/Token 값은 Log에 남기지 않는다.

### 내부 정보 미노출

`handleExceptionInternal`은 `BusinessException`을 제외하고 항상 `ErrorCode.defaultMessage`(고정 문구)를 사용하며, 예상하지 못한 예외의 실제 `message`는 Client 응답에 포함하지 않는다. `BusinessException`의 Message는 예외 자체가 아니라 그 Message를 직접 작성한 코드(우리 Application 코드)를 신뢰한 것이므로 예외로 둔다. Stack Trace, Exception Class 이름은 어떤 경우에도 응답 Body에 없다. [`GlobalExceptionHandlerTest`](../../src/test/kotlin/team/inreok/getiserver/global/error/GlobalExceptionHandlerTest.kt)의 "예상하지 못한 오류는 500으로 처리되며 내부 정보를 노출하지 않는다" Test로 검증했다.

## RequestId

[`RequestIdFilter`](../../src/main/kotlin/team/inreok/getiserver/global/web/RequestIdFilter.kt)(`OncePerRequestFilter`, `@Order(Ordered.HIGHEST_PRECEDENCE)`)가 모든 요청에서 가장 먼저 실행되어 `requestId`를 만든다.

- Client가 `X-Request-Id` Header를 보냈다면 새로 만들지 않고 그대로 재사용한다(여러 Service에 걸친 요청을 하나의 requestId로 추적할 수 있게 함).
- Client가 보내지 않았다면 `UUID.randomUUID()`로 새로 생성한다.
- 생성한 값은 Response Header(`X-Request-Id`)에 그대로 반환하고, SLF4J MDC(`requestId` Key)에 등록해 요청 처리 중 발생하는 모든 Log에 남긴다(Log Pattern에 `%X{requestId}` 등을 추가하면 실제로 출력된다).
- `ApiResponse`/`ErrorResponse`는 생성 시점에 MDC에서 값을 읽어 Body의 `meta.requestId` Field로도 포함한다. 성공/오류 응답이 항상 같은 값을 가지므로 Client 로그와 서버 로그를 하나의 requestId로 연결할 수 있다.
- Filter가 끝나면 `MDC.remove(...)`로 값을 제거해 Thread Pool 재사용 시 이전 요청의 requestId가 새 요청에 섞이지 않게 한다.

새로운 분산 추적(Trace ID Propagation, OpenTelemetry 등) 시스템은 도입하지 않았다 — `requestId`는 이 저장소 안에서 로그와 응답을 연결하는 최소 식별자다. Micrometer Tracing/OpenTelemetry 도입 여부는 Observability 전용 PR에서 재검토한다.

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

`page`는 Spring Data와 동일하게 0-based를 유지한다(내부 Repository 조회와 외부 API 응답 사이의 별도 변환이 없어 실수 위험이 적다). 최대 Page Size는 `WebPageableConfig`(`PageableHandlerMethodArgumentResolverCustomizer`)가 100으로 강제한다(GETI Notion API 명세서 "목록 기본값: page=0, size=20, 최대 size=100" 반영, [`docs/audit/notion-repository-sync.md`](../audit/notion-repository-sync.md) 참고). `size`가 100을 넘으면 Spring Data가 자동으로 100으로 잘라낸다. `@WebMvcTest`에서 이 Bean을 검증하려면 `@Import(WebPageableConfig::class)`가 필요하다 — `@WebMvcTest`는 `@Controller`/`@ControllerAdvice`/`WebMvcConfigurer` 등 특정 Stereotype만 자동 인식하고 일반 `@Configuration`은 인식하지 않기 때문이다(`GlobalExceptionHandlerTest`의 최대 Page Size Test로 실측 확인).

`PageResponse`는 `ApiResponse`로 다시 감싸지 않고 위 예시처럼 그 자체를 응답 Body로 반환한다(`success` Field, `meta.requestId`가 없다). 실제 Member 도메인의 목록 API(예: `GET /api/v1/members`)는 아직 `PageResponse`를 사용하지 않고, `content`/`page`/`size`/`totalElements`/`totalPages`/`first`/`last` Field를 직접 담은 응답 DTO를 `ApiResponse`의 `data`로 반환한다(Notion API 명세서의 목록 응답 형식을 그대로 따름). 두 Pagination 표현 방식(`PageResponse`의 `data`/`meta.page`, Member 도메인 DTO의 평평한 Field)이 아직 하나로 통일되지 않았다는 점에 유의한다.

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
| [`GlobalExceptionHandlerTest`](../../src/test/kotlin/team/inreok/getiserver/global/error/GlobalExceptionHandlerTest.kt) | 성공/오류 응답, Pagination, Validation, Malformed JSON, 405, 415, Type Mismatch, 필수 Parameter 누락, 404, 500, BusinessException, requestId | `@WebMvcTest` + `MockMvc` |
| [`WebCorsConfigTest`](../../src/test/kotlin/team/inreok/getiserver/global/web/WebCorsConfigTest.kt) | CORS 허용/비허용 Origin Preflight | `@WebMvcTest` + `@TestPropertySource` |
| [`RequestIdFilterTest`](../../src/test/kotlin/team/inreok/getiserver/global/web/RequestIdFilterTest.kt) | requestId 생성, Client 값 재사용, 성공/오류 응답 모두 포함 | `@WebMvcTest` + `MockMvc` |

세 Test 모두 `WebTestSupportController`(`src/test`에만 존재)를 대상으로 한다. Production Source에는 예시 Controller를 두지 않았다. `WebTestSupportController`와 Test Class들은 모두 `src/test` Source Set에 있어 `main` Classpath에 포함되지 않고 Spring Modulith Production Module 탐지 대상이 아니다.

## 검증 명령

```bash
./gradlew spotlessCheck
./gradlew detekt
./gradlew test
./gradlew test --tests "*ModularityTest"
./gradlew test --tests "*GlobalExceptionHandlerTest*" --tests "*WebCorsConfigTest*" --tests "*RequestIdFilterTest*"
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
- 분산 추적(Trace ID Propagation, OpenTelemetry, Micrometer Tracing), 전체 Observability(`requestId`는 이번 PR에서 구현)
- REST Docs, Contract Test
- GitHub Actions CI, CD
