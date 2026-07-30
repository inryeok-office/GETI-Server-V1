# Spring Boot / Kotlin 작업 원칙 (Claude Code)

GETI-Server의 실제 환경(Spring Boot 4.1.0, Kotlin 2.3.21, Gradle 9.5.1 Kotlin DSL, Java Toolchain 25, Root Package `team.inreok.getiserver`)을 기준으로 한 Claude Code 전용 작업 규칙이다. 아직 도입되지 않은 Architecture를 확정된 규칙처럼 강제하지 않는다 ([`docs/ai/coding-conventions.md`](../../docs/ai/coding-conventions.md) 참고).

## 버전과 Build

- 현재 Java, Spring Boot, Kotlin 버전을 근거 없이 변경하지 않는다.
- 현재 Gradle Kotlin DSL(`build.gradle.kts`, `settings.gradle.kts`)을 유지한다.
- 기존 Plugin과 Dependency를 확인한 뒤 작업한다.
- 새 Dependency를 추가할 때는 필요성과 영향(Runtime, Test Scope 여부, 유지보수 상태)을 검토한다.

## 기존 구조 우선

- 기존 Root Package(`team.inreok.getiserver`)와 하위 Package 구조를 확인한다.
- 기존 Naming, Component/Configuration 구조를 확인한다.
- 기존 Test 위치(`src/test/kotlin/...`)를 확인한다.
- `GetiServerApplication`(Main Application Class) 위치를 임의로 이동하지 않는다.

## Spring 구성 원칙

- Component Scan 범위를 임의로 변경하지 않는다.
- 설정값을 Source Code에 Hard Coding하지 않는다.
- 환경별로 달라지는 값은 공통 설정(`application.yaml`)이 아닌 Profile(`local`/`test`/`prod`) 또는 환경 변수로 분리한다. Profile 전략과 환경 변수 Naming, Secret 관리 기준은 [`docs/development/configuration.md`](../../docs/development/configuration.md)를 따른다.
- Secret은 환경 변수로만 참조하고, 안전하지 않은 기본값(`${SECRET:change-me}` 등)을 제공하지 않는다.
- Bean 충돌을 임시 이름 변경만으로 숨기지 않는다.
- Circular Dependency를 `@Lazy`로 무조건 우회하지 않는다.
- Spring Context 실패를 Test 비활성화로 숨기지 않는다.
- 새 Starter를 추가하기 전에 기존 Dependency로 가능한지 확인한다.
- `spring.jpa.hibernate.ddl-auto`는 `validate` 또는 `none`만 사용한다(`create`/`create-drop`/`update` 금지). Schema는 Flyway Migration으로만 관리하고, 이미 병합된 Migration 파일은 수정하지 않고 새 버전을 추가한다.
- `spring.jpa.open-in-view=false`를 유지하고 Controller에서 Transaction을 시작하지 않는다.
- 외부 API 호출, 파일 업로드/다운로드 등 느리거나 실패할 수 있는 I/O를 DB Transaction(`@Transactional`) 내부에서 수행하지 않는다. Transaction은 Application/Service 계층에서 최소 범위로 유지한다(GETI Notion BE 컨벤션 "17. Transaction Convention" 확정).
- `spring.flyway.clean-disabled=true`를 임의로 되돌리지 않는다.
- Redis는 우선 Spring Boot가 기본 제공하는 `StringRedisTemplate`을 사용한다. 실제 객체 직렬화가 필요해지기 전에는 Java 직렬화 기반 `RedisTemplate<String, Any>` 같은 범용 Bean을 미리 만들지 않는다.
- Docker(Testcontainers)가 필요한 PostgreSQL/Redis Persistence Integration Test는 `src/test`가 아니라 `src/integrationTest`(`./gradlew integrationTest`)에 작성한다. 자세한 내용은 [`docs/development/persistence.md`](../../docs/development/persistence.md)를 따른다.
- 새 Controller와 요청/응답 DTO는 공통 Package(`team.inreok.getiserver.global.web`, `team.inreok.getiserver.global.error`)가 아니라 해당 Domain Package(`team.inreok.getiserver.domain.{domain-name}`) 내부에 둔다. Controller에 비즈니스 로직, Repository 직접 호출, Transaction 시작을 두지 않는다.
- API 응답은 `ApiResponse`/`PageResponse`(`team.inreok.getiserver.global.web`), `ErrorResponse`(`team.inreok.getiserver.global.error`)를 사용한다. JPA Entity를 응답으로 직접 반환하거나 `Map<String, Any>`를 응답으로 사용하거나 `Page<T>`를 그대로 반환하지 않는다.
- 새 Error Code는 실제로 처리하는 오류에만 추가한다. Domain Error Code는 해당 Domain Module 내부에서 정의한다. Domain 예외는 `global.error.BusinessException`을 상속해 정의하고, 특정 Domain 예외를 `global` Package 안에 미리 만들지 않는다.
- Exception Message, Stack Trace를 오류 응답에 그대로 노출하지 않는다(단, `BusinessException`의 Message는 우리 코드가 직접 작성한 안전한 문구이므로 예외). 자세한 내용은 [`docs/development/web-api.md`](../../docs/development/web-api.md)를 따른다.

## 모듈 경계 (Spring Modulith)

Spring Modulith 기반이 구성되어 있다(`spring-modulith-starter-test`, `ModularityTest`, `PackageArchitectureTest`). 최상위 Production Package는 `domain`과 `global` 두 종류만 사용하고, 새 도메인 Package는 `domain` 바로 아래 독립된 Module(`domain.{domain-name}`)로 만든다. Domain 내부는 `entity`(+`entity/type`), `repository`, `service`, `controller`, `dto`, `exception` 중 실제로 필요한 Package만 만든다. 다른 Domain의 내부 구현을 직접 참조하지 않는다(Named Interface로 명시적으로 공개한 Package는 예외, 현재 `domain.operation.entity.type`만 해당). Package 구조를 바꾸면 `./gradlew test --tests "*ModularityTest"`와 `./gradlew test --tests "*PackageArchitectureTest"`로 구조 검증을 실행한다. 세부 원칙은 [`docs/architecture/modularity.md`](../../docs/architecture/modularity.md)를 따른다.

## Architecture 제한

Domain Package 내부 구조(`entity`/`repository`/`service`/`controller`/`dto`/`exception`)와 최상위 `domain`/`global` 분리는 사용자가 확정했다([`docs/architecture/modularity.md`](../../docs/architecture/modularity.md) 참고). 아래 항목은 여전히 이 저장소에 확정되지 않았다. 관련 Issue 없이 전역 규칙처럼 강제하거나 임의로 구현하지 않는다.

```text
Controller-Service-Repository 고정 구조
Hexagonal Architecture
Clean Architecture
JPA Entity 공통 Base Class(global.persistence)
QueryDSL 구조
OpenAPI(springdoc) 실제 도입
Security Filter Chain 구조
OAuth 및 JWT 구조
```

위 항목은 향후 전용 PR 또는 Issue에서 확정된 뒤 [`docs/ai/coding-conventions.md`](../../docs/ai/coding-conventions.md)가 갱신되면 반영한다.

## 코드 생성 제한

- 사용하지 않는 Class를 생성하지 않는다.
- 빈 Package를 생성하지 않는다.
- 아직 필요하지 않은 미래 기능을 예상한 Placeholder를 생성하지 않는다.
- 컴파일만 통과시키는 임시 구현을 남기지 않는다.
- 의미 없는 Interface 분리를 하지 않는다.
- 한 번만 사용하는 단순 구현을 과도하게 추상화하지 않는다.

## Dependency 추가

새 Dependency를 추가할 때 다음을 확인한다.

- 기존 Dependency로 가능한지
- Spring Boot 공식 Starter가 있는지
- 현재 Spring Boot/Kotlin 버전과의 호환성
- 보안 및 유지보수 상태
- Runtime에 미치는 영향
- Test Scope(`testImplementation`, `testRuntimeOnly`)로 충분한지
- 실제 Issue 범위에 필요한 것인지

이 문서를 포함해 이번 AI 하네스 구성 단계에서는 실제 비즈니스 Dependency를 추가하지 않는다.
