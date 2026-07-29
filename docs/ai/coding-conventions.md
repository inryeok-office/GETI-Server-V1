# 코딩 컨벤션 (AI 작업 원칙)

GETI-Server는 기반 구축(PR 1~10)을 마쳤고 아직 실제 Domain 기능은 구현하지 않았다. Domain Module 내부 구조(DDD Layer)는 GETI Notion 컨벤션으로 확정되어 있다(아래 "Domain Module 내부 구조" 참고). 이 문서는 현재 실제로 확정된 공통 원칙만 다룬다.

## 공통 원칙

- 기존 코드에 이미 스타일이 존재한다면 새 코드보다 기존 스타일을 우선한다.
- 현재 프로젝트가 사용하는 Java, Kotlin, Spring Boot 버전을 근거 없이 변경하지 않는다.
- 불필요한 추상화를 만들지 않는다. 하나의 사용처만 있는 기능을 미리 일반화하지 않는다.
- Class, 함수, 변수 이름은 역할이 드러나도록 의미 있게 작성한다.
- 이미 구현된 기능을 확인하지 않고 중복 구현하지 않는다.
- 요청받은 작업과 관련 없는 Refactoring을 함께 수행하지 않는다.
- 공개 API(Controller, 외부에 노출되는 함수/클래스 Signature)를 변경하기 전에 호출하는 곳과 영향 범위를 확인한다.
- 의미 없는 주석과 Javadoc/KDoc을 남발하지 않는다. 코드로 설명되지 않는 이유(왜 이렇게 했는지)가 있을 때만 주석을 남긴다.
- 컴파일 경고를 없애기 위해 `@SuppressWarnings` 등을 근거 없이 추가하지 않는다.
- 컴파일 오류나 경고를 숨기거나 우회하지 않고, 원인을 해결한다.
- 사용되지 않는 Class, 빈 Package, 임시로 남겨둔 Placeholder 코드를 만들지 않는다.
- 새 Dependency를 추가하기 전에 기존 Dependency로 대체할 수 있는지 먼저 확인한다.

## 코드 스타일과 정적 분석

Kotlin Source의 포맷은 EditorConfig(`.editorconfig`)와 Spotless(ktlint)로, 정적 분석은 detekt로 자동 검사한다. 도구가 검사하는 항목(공백, Import 정렬, 코드 스멜 등)을 수동으로 재판단하지 않고 `./gradlew spotlessApply`, `./gradlew spotlessCheck`, `./gradlew detekt`를 사용한다. 도구별 설정과 명령은 [`docs/development/code-quality.md`](../development/code-quality.md)를 따른다.

## 모듈 경계 (Spring Modulith)

새 도메인 기능은 Root Package(`team.inreok.getiserver`) 바로 아래의 독립된 Package(Application Module 후보)에 구현한다. 다른 Module의 내부 구현 Package를 직접 참조하지 않고, 순환 의존성을 만들지 않는다. `common`/`global` Package에는 여러 Module이 실제로 공유하는 기술 요소만 두고 특정 도메인 로직을 넣지 않는다. Configuration/Infrastructure/Support 등 기술 기반 Package는 실제 Class가 생기는 시점에만 만든다. Package를 추가하거나 옮긴 뒤에는 `./gradlew test --tests "*ModularityTest"`로 구조 검증을 실행한다. 세부 원칙과 현재 상태(Package Tree, 기술 기반 Package 생성 기준, 만들지 않는 Package 목록)는 [`docs/architecture/modularity.md`](../architecture/modularity.md)를 따른다.

## Configuration과 Profile

환경별로 달라지는 값은 공통 설정(`application.yaml`)에 넣지 않고 `local`/`test`/`prod` Profile 또는 환경 변수로 분리한다. Secret(Password, Token, Key 등)은 코드나 설정 파일에 실제 값으로 작성하지 않고 환경 변수로만 참조하며, 안전하지 않은 기본값을 제공하지 않는다. `.env`는 Spring Boot가 자동으로 읽는 파일이 아니다. Profile 전략, 환경 변수 Naming Convention, `@ConfigurationProperties` 도입 기준은 [`docs/development/configuration.md`](../development/configuration.md)를 따른다.

## Persistence (JPA / Flyway / Redis)

PostgreSQL(Spring Data JPA + Flyway)과 Redis(Lettuce) 연결 기반이 구성되어 있다. 새 Entity/Repository는 Root Package 바로 아래 공용 `entity`/`repository` Package가 아니라 해당 Domain Module Package 안에 둔다. Schema 변경은 Flyway Migration으로만 하고 `spring.jpa.hibernate.ddl-auto`를 `create`/`create-drop`/`update`로 바꾸지 않는다(`validate`/`none`만 사용). 이미 병합된 Migration 파일의 내용은 수정하지 않고 새 버전을 추가한다. `spring.jpa.open-in-view=false`를 유지하고 Controller에서 Transaction을 시작하지 않는다. Redis는 우선 Spring Boot가 기본 제공하는 `StringRedisTemplate`을 사용하고, 실제 객체 직렬화가 필요해지는 시점에 방식(JSON 등)을 판단한다. Docker(Testcontainers)가 필요한 Persistence Integration Test는 `test`/`check`/`build`가 아니라 별도 `integrationTest` Gradle Task로 작성한다. 세부 환경 변수, 정책 근거, Integration Test 구조는 [`docs/development/persistence.md`](../development/persistence.md)를 따른다.

## Web / API 공통 기반

공통 성공 응답(`ApiResponse`), Pagination 응답(`PageResponse`), CORS(`CorsProperties`/`WebCorsConfig`), requestId 생성(`RequestIdFilter`)이 `team.inreok.getiserver.global.web`에, 오류 응답(`ErrorResponse`/`FieldErrorResponse`), Framework Error Code(`ErrorCode`), 공통 예외 기반(`BusinessException`), 전역 예외 처리(`GlobalExceptionHandler`)가 `team.inreok.getiserver.global.error`에 구성되어 있다(PR 9에서 `web` 하나로 시작, PR 12에서 `global.web`/`global.error`로 재구성). 새 Domain Controller와 요청/응답 DTO는 이 공용 Package가 아니라 해당 Domain Module 내부에 둔다. Controller에 비즈니스 로직을 작성하거나 Repository를 직접 호출하거나 Transaction을 시작하지 않는다. JPA Entity를 API Response로 직접 반환하지 않고, `Map<String, Any>`를 공통 응답으로 쓰지 않으며, `Page<T>`를 API에 직접 반환하지 않고 `PageResponse.of(page)`로 변환한다. Domain 예외는 `BusinessException`을 상속해 각 Domain Module 내부에 정의한다(`global`에는 특정 Domain 예외를 두지 않는다). 새 Error Code는 실제로 처리하는 오류에만 추가한다. Exception Message를 검증 없이 그대로 Client에 반환하지 않는다(`BusinessException`의 Message는 우리 코드가 직접 작성한 안전한 문구라 예외). 시간 값은 `Instant` + UTC ISO-8601을 우선하며, CORS는 `allowedOrigins`가 비어 있으면 비활성 상태를 유지하고 Wildcard(`*`)와 `allowCredentials=true`를 함께 쓰지 않는다. 성공/오류 응답 모두 `RequestIdFilter`가 MDC에 남긴 `requestId`를 Body와 `X-Request-Id` Header에 포함한다. 새 API를 구현하면 Web Slice Test(`@WebMvcTest`)와 오류 Contract Test를 함께 작성한다. 세부 정책과 근거는 [`docs/development/web-api.md`](../development/web-api.md)를 따른다.

## Domain Module 내부 구조 (DDD, GETI Notion 컨벤션 확정)

GETI Notion BE 컨벤션이 Domain Module 내부 구조를 domain/application/infrastructure/presentation 4-Layer DDD로 확정했다. 실제 Domain Module을 처음 만들 때부터 이 구조를 적용한다. 세부 내용은 [`docs/architecture/modularity.md`](../architecture/modularity.md)의 "Domain Module 내부 구조" Section을 따른다. 아직 실제 Domain Module이 없어 이 구조로 만들어진 Package는 없다.

## 아직 확정되지 않은 규칙

다음 항목은 이 저장소에 아직 도입되지 않았다. 확정된 규칙인 것처럼 강제하거나 임의로 구현하지 않는다.

```text
JPA Entity 상세 설계(공통 Base Class, ID 생성 전략 표준 등)
Spring Security 구조
OpenAPI(springdoc) 실제 도입
```

QueryDSL, ArchUnit, Mockito는 GETI Notion Tech Stack이 확정 도구로 명시하지만, 아직 이 저장소에 실제로 사용할 대상(복잡한 동적 조회, 2개 이상 Domain Module 간 교차 참조 규칙, Mocking이 필요한 Service Test)이 없어 Dependency를 미리 추가하지 않았다. 실제로 필요한 시점에 도입하고, 그때 이 목록에서 제거한다.

위 항목은 추후 Architecture 관련 PR에서 결정되고 문서화될 예정이다. 관련 작업이 필요한 Issue를 받으면, 이 문서가 갱신되기 전까지는 최소한의 구현만 하고 확정된 규칙처럼 문서화하지 않는다.
