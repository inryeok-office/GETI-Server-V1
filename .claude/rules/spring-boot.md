# Spring Boot / Kotlin 작업 원칙 (Claude Code)

GETI-Server의 실제 환경(Spring Boot 4.1.0, Kotlin 2.3.21, Gradle 9.5.1 Kotlin DSL, Java Toolchain 25, Root Package `team.inreok.geti.getiserver`)을 기준으로 한 Claude Code 전용 작업 규칙이다. 아직 도입되지 않은 Architecture를 확정된 규칙처럼 강제하지 않는다 ([`docs/ai/coding-conventions.md`](../../docs/ai/coding-conventions.md) 참고).

## 버전과 Build

- 현재 Java, Spring Boot, Kotlin 버전을 근거 없이 변경하지 않는다.
- 현재 Gradle Kotlin DSL(`build.gradle.kts`, `settings.gradle.kts`)을 유지한다.
- 기존 Plugin과 Dependency를 확인한 뒤 작업한다.
- 새 Dependency를 추가할 때는 필요성과 영향(Runtime, Test Scope 여부, 유지보수 상태)을 검토한다.

## 기존 구조 우선

- 기존 Root Package(`team.inreok.geti.getiserver`)와 하위 Package 구조를 확인한다.
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

## 모듈 경계 (Spring Modulith)

Spring Modulith 기반이 구성되어 있다(`spring-modulith-starter-test`, `ModularityTest`). 새 도메인 Package는 Root Package 바로 아래 독립된 Module로 만들고, 다른 Module의 내부 구현을 직접 참조하지 않는다. Package 구조를 바꾸면 `./gradlew test --tests "*ModularityTest"`로 구조 검증을 실행한다. 세부 원칙은 [`docs/architecture/modularity.md`](../../docs/architecture/modularity.md)를 따른다.

## Architecture 제한

아래 항목은 이 저장소에 아직 확정되지 않았다. 관련 Issue 없이 전역 규칙처럼 강제하거나 임의로 구현하지 않는다.

```text
Module 내부 상세 Package 구조 (api/internal 등)
Controller-Service-Repository 고정 구조
Hexagonal Architecture
Clean Architecture
JPA Entity 공통 Base Class
QueryDSL 구조
공통 API Response 구조
Global Exception Handler 구조
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
