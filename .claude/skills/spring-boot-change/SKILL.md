---
name: spring-boot-change
description: Spring Boot/Kotlin 프로젝트를 변경할 때 기존 구조와 버전을 보존하면서 안전하게 구현하기 위한 환경 분석, 기존 구현 탐색, 변경 판단, 구현 원칙을 다룬다.
---

# Spring Boot Change

GETI-Server의 Spring Boot/Kotlin 코드를 변경할 때 참고하는 상세 판단 기준이다. [`implement` Command](../../commands/implement.md), [`fix-bug` Command](../../commands/fix-bug.md)가 이 Skill을 참조한다.

## 환경 분석

변경 전 실제 환경을 확인한다. 확인하지 않고 가정하지 않는다.

- Java 버전 (`build.gradle.kts`의 Toolchain)
- Spring Boot 버전
- Gradle 버전과 DSL(Kotlin DSL)
- Root Package (`team.inreok.getiserver`)
- 적용된 Plugin
- 선언된 Dependency
- Profile 구성 (`local`/`test`/`prod`, [`docs/development/configuration.md`](../../../docs/development/configuration.md) 참고)
- Test Framework (JUnit 5, Spring Boot Test)

## 기존 구현 탐색

구현 전에 다음을 검색한다.

- 유사 Controller
- 유사 Service
- 유사 Repository
- Configuration Class
- Exception 처리 방식
- Response 형식
- Test 작성 패턴

이 저장소는 아직 초기 단계라 위 항목 다수가 존재하지 않을 수 있다. 존재하지 않는 구조를 가정해서 만들어내지 않는다.

## 변경 판단

다음을 확인한 뒤 구현 방식을 결정한다.

- 새 Dependency가 실제로 필요한지, Spring 공식 기능만으로 가능한지
- 설정 변경이 다른 부분에 미치는 영향
- Bean 충돌 가능성
- Component Scan에 미치는 영향
- Profile에 미치는 영향 (환경별 값은 공통 설정이 아닌 `local`/`test`/`prod` Profile 또는 환경 변수로 분리, [`docs/development/configuration.md`](../../../docs/development/configuration.md) 참고)
- 공개 API(Controller Signature 등) 호환성
- Migration이 필요한지 (Flyway가 Schema를 관리한다. `ddl-auto`는 `validate`/`none`만 사용하고, 병합된 Migration 파일은 수정하지 않고 새 버전을 추가한다. [`docs/development/persistence.md`](../../../docs/development/persistence.md) 참고)

## 구현 원칙

- 최소 변경으로 요구사항을 충족한다.
- 기존 Pattern을 우선한다.
- 불필요한 추상화를 만들지 않는다.
- 설정값을 Source Code에 Hard Coding하지 않는다.
- Placeholder나 TODO로 핵심 요구사항을 남기지 않는다.
- 문제를 임시로 우회(예: `@Lazy` 남용, Exception 무조건 Catch)하지 않는다.
- 의미 없는 Interface 분리를 하지 않는다.
- 관련 없는 Package 이동을 함께 하지 않는다.
- 새 도메인 Package는 Root Package 바로 아래 독립된 Application Module로 만들고, 다른 Module의 내부 구현을 직접 참조하지 않는다 ([`docs/architecture/modularity.md`](../../../docs/architecture/modularity.md) 참고).
- Entity/Repository는 Root Package 바로 아래 공용 `entity`/`repository` Package가 아니라 해당 Domain Module 안에 둔다. `spring.jpa.hibernate.ddl-auto`를 `create`/`create-drop`/`update`로 바꾸지 않는다. Docker(Testcontainers)가 필요한 Persistence Test는 `src/test`가 아니라 `src/integrationTest`에 작성한다 ([`docs/development/persistence.md`](../../../docs/development/persistence.md) 참고).
- Controller와 요청/응답 DTO는 Root Package 바로 아래 공용 `controller`/`dto` Package가 아니라 해당 Domain Module 안에 둔다. 응답은 `ApiResponse`/`PageResponse`(`team.inreok.getiserver.global.web`), `ErrorResponse`(`team.inreok.getiserver.global.error`)를 사용하고, JPA Entity·`Map<String, Any>`·`Page<T>`를 API에 직접 반환하지 않는다. 새 Error Code는 실제로 처리하는 오류에만 추가한다. Domain 예외는 `global.error.BusinessException`을 상속해 해당 Domain Module 안에 정의한다 ([`docs/development/web-api.md`](../../../docs/development/web-api.md) 참고).

## Architecture 제한

아래 항목은 이 저장소에 아직 확정되지 않았다. 관련 전용 Issue 없이 전역 규칙처럼 강제하거나 임의로 도입하지 않는다.

```text
Module 내부 상세 Package 구조 (api/internal 등)
Controller-Service-Repository 고정 구조
Hexagonal Architecture / Clean Architecture
JPA Entity 공통 Base Class
QueryDSL 구조
OpenAPI(springdoc) 실제 도입
Security Filter Chain 구조
OAuth 및 JWT 구조
```

([`docs/ai/coding-conventions.md`](../../../docs/ai/coding-conventions.md), [`docs/architecture/modularity.md`](../../../docs/architecture/modularity.md) 참고)

## 검증

- Compile
- 관련 Test
- Spring Context 정상 로드
- Package를 옮기거나 새 Module을 추가했다면 `./gradlew test --tests "*ModularityTest"`로 모듈 경계 검증
- 전체 Test
- Build
- 변경한 설정 파일(`application.yaml` 등) 검토
- Diff 리뷰
