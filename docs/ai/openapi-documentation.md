# OpenAPI(Swagger) 문서화 정책

이 문서는 GETI-Server의 API를 Swagger UI로 문서화하고 검증하는 방법을 정의하는 Canonical 문서다. API 또는 Controller를 추가·변경하는 모든 작업은 이 문서의 규칙을 따른다. `AGENTS.md`, `CLAUDE.md`, `.claude/rules/spring-boot.md`, `.codex/policies/*`는 이 문서를 필수로 참조하며 내용을 중복 기술하지 않는다.

## 배경

프론트엔드와 앱 개발자가 Backend 코드나 Notion 문서를 추가로 확인하지 않고 Swagger UI만으로 API 계약(Request/Response, 인증, 오류)을 정확히 이해하고 호출할 수 있어야 한다. 이를 위해 다음 두 가지를 함께 강제한다.

1. 현재 구현된 모든 API에 구체적인 Swagger Annotation을 적용한다.
2. `OpenApiDocumentationTest`(`src/test/kotlin/team/inreok/getiserver/OpenApiDocumentationTest.kt`)가 새 API의 문서화 누락을 자동으로 잡아낸다. 이 Test는 별도 Task 없이 `./gradlew test`/`check`/`build`에 포함되어 항상 실행된다.

## 필수 규칙

API(Controller Endpoint) 하나를 추가하거나 변경하면 **같은 PR** 안에서 다음을 함께 한다.

- **모든 Controller**: `@Tag(name = ..., description = ...)`로 소속 도메인과 용도를 설명한다. 같은 이름의 `@Tag`는 여러 Controller에서 공유할 수 있다(Swagger UI에서 하나의 그룹으로 묶임).
- **모든 Endpoint(`@GetMapping` 등)**: `@Operation(summary = ..., description = ...)`을 반드시 작성한다.
  - `summary`는 한 줄 요약, `description`은 동작 조건·부분 수정 규칙·예외 상황 등 실제로 호출할 때 필요한 세부 사항을 담는다.
  - "조회 API", "등록 API"처럼 의미 없는 설명을 쓰지 않는다. "로그인한 본인의 프로필을 조회한다"처럼 실제 동작을 구체적으로 쓴다.
- **인증이 필요한 Endpoint**: `@SecurityRequirement(name = BEARER_AUTH_SCHEME)`(`team.inreok.getiserver.global.openapi.BEARER_AUTH_SCHEME`)를 붙인다. 공개 Endpoint에는 붙이지 않는다.
- **모든 Path/Query/Header Parameter**: `@Parameter(description = ..., example = ...)`를 붙인다. 선택 값이면 그 사실과 기본값·허용 범위를 `description`에 명시한다.
- **모든 Request/Response DTO Class와 Field**: `@Schema(description = ..., example = ...)`를 붙인다. `nullable = true`, `maxLength`, Enum 등 실제 제약을 최대한 반영한다. 실제 JSON 예시(`example`)를 넣어 Type 이름(`"Long"`, `"String"`)을 값으로 쓰지 않는다.
- **정상/오류 응답**: `@ApiResponses` + `@ApiResponse`(`io.swagger.v3.oas.annotations.responses`, `SwaggerApiResponse`로 Import Alias 권장 — Spring MVC의 `@ResponseBody` 계열 `ApiResponse`와 이름이 겹친다)로 실제로 발생하는 HTTP Status와 Error Code만 적는다. 코드에 없는 오류를 미리 추가하지 않는다.
- **JsonNode 등 고정 DTO가 아닌 Request Body**(예: `MemberProfileController`의 부분 수정)는 문서 전용 Schema Class를 만들어 `@RequestBody(content = [Content(schema = Schema(implementation = ...))])`로 실제 허용 Field를 보여준다. 이 Class는 Production 로직에서 사용하지 않는 순수 문서용이라는 점을 주석으로 남긴다.
- Entity를 Response로 직접 노출하지 않는다(기존 `docs/development/web-api.md` 원칙과 동일).

## 검증

```bash
./gradlew test --tests "*OpenApiDocumentationTest*"
./gradlew test    # 전체 Test 실행에 포함됨
./gradlew check   # test를 포함하므로 자동으로 함께 실행됨
```

`OpenApiDocumentationTest`는 `/v3/api-docs`를 직접 생성해 다음을 검증한다.

- 실제 등록된 모든 API Endpoint(`RequestMappingHandlerMapping` 기준, `/error`·`/test/**`·`/v3/api-docs`·`/swagger-ui`·`/actuator`는 문서화 대상이 아니므로 제외)가 OpenAPI 문서의 `paths`에 존재하는지.
- 모든 Operation에 Tag, `summary`, `description`, 최소 하나의 2xx 성공 Response가 있는지.
- 모든 Path/Query Parameter에 `description`이 있는지.
- `/api/v1/me/**`, `/api/v1/auth/session`, `/api/v1/auth/logout`, `/api/v1/members**`처럼 `SecurityConfig`가 인증을 요구하는 경로에 `security`(SecurityRequirement)가 선언되어 있는지.

이 Test가 실패하면 Annotation을 채워서 통과시킨다. **Swagger 문서화가 누락된 상태에서는 그 API 작업을 완료로 보고하지 않는다.**

의도적으로 문서화 대상에서 제외해야 하는 실제 API Endpoint가 생기면(현재는 없음), `OpenApiDocumentationTest`의 `HIDDEN_ENDPOINTS`에 경로와 제외 사유를 함께 추가한다. 이유 없이 비워두거나 검증을 우회하지 않는다.

## 환경별 노출

- `application.yaml`(공통)에서 `springdoc.api-docs.enabled`/`springdoc.swagger-ui.enabled`를 `true`로 둔다(Local, 별도 Profile 없는 실행 모두 포함).
- `application-prod.yaml`에서 두 값을 `false`로 재정의해 운영 환경에는 노출하지 않는다.
- Swagger 경로(`/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html`)는 `SecurityConfig`에서 명시적으로 `permitAll`로 선언한다. 이 경로를 열기 위해 다른 실제 API의 인증을 완화하지 않는다.

## Notion API 명세서와 다를 때

Swagger는 **저장소에 실제 구현된 동작**을 정확히 문서화하는 것이 목적이다. `AGENTS.md`의 우선순위(사용자 요청 > 현재 Issue > Notion 확정 요구사항 > 저장소 실제 구현)를 따르되, Swagger 작업 자체에서 코드의 비즈니스 동작을 임의로 Notion에 맞춰 바꾸지 않는다. 차이를 발견하면 `docs/audit/notion-repository-sync.md`의 분류 기준(`CONTRACT_MISMATCH`/`STALE_NOTION` 등)에 따라 기록하고 PR 본문에 남긴다. 실제 사례는 PR #52(Swagger 문서화)의 "Notion과 코드 차이" 절 참고.

## 로컬 확인 방법

```text
Swagger UI: http://localhost:8080/swagger-ui/index.html
OpenAPI JSON: http://localhost:8080/v3/api-docs
```

우측 상단 Authorize 버튼에 `accessToken`(Bearer 접두어 없이 값만) 입력하면 인증이 필요한 Endpoint도 Swagger UI에서 바로 호출할 수 있다.

## 관련 문서

- [`docs/development/web-api.md`](../development/web-api.md) — 공통 응답/오류 구조, Entity 미노출 원칙
- [`docs/audit/notion-repository-sync.md`](../audit/notion-repository-sync.md) — Notion과 저장소 차이 분류 기준
- [`docs/ai/coding-conventions.md`](./coding-conventions.md) — 코드 작성 범위 원칙(Swagger Annotation도 이 범위 원칙을 따름)
