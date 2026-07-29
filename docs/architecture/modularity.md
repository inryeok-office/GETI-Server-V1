# 모듈 구조 (Spring Modulith)

GETI-Server는 향후 도메인 기능이 늘어날 때 Package 기반 논리 모듈(Application Module) 경계를 명확하게 유지하기 위해 [Spring Modulith](https://spring.io/projects/spring-modulith)를 사용한다.

Spring Modulith 도입은 즉시 MSA로 분리한다는 뜻이 아니다. 현재 단계에서는 하나의 배포 단위(Modular Monolith) 안에서 모듈 경계를 명시적으로 만들고, 향후 서비스 분리가 실제로 필요할 때 판단할 수 있는 기반을 마련하는 목적이다.

## 현재 상태

- Root Package: `team.inreok.getiserver`
- 이 Package 바로 아래에는 `GetiServerApplication`(Application 진입점) 하나만 있고, 하위 Package(도메인 후보)가 아직 없다.
- 따라서 `ApplicationModules.of(GetiServerApplication::class.java)`로 탐지되는 Application Module은 현재 **0개**다. 이는 실제 측정 결과이며, 모듈을 만들기 위해 가짜 Package나 Marker Class를 추가하지 않았다.
- 도메인 Package가 추가되면 별도 설정 변경 없이 Root Package 바로 아래 Package가 자동으로 Application Module로 탐지된다(Spring Modulith 기본 탐지 전략, Direct Subpackage 기준).

## Package Tree

실제 Production/Test Package 구조는 다음과 같다(2026-07-29 기준, Package Structure Refactoring 검토 시점).

```text
team.inreok.getiserver                            (Root Package)
├── GetiServerApplication.kt                      (Production, Application 진입점)
├── GetiServerApplicationTests.kt                 (Test, Application Context Smoke Test)
├── ModularityTest.kt                             (Test, 구조 검증)
├── ModuleDocumentationTest.kt                     (Test, 문서 생성)
└── ApplicationProfileConfigurationTest.kt         (Test, Profile Config Data Binding 검증)
```

Root Package 바로 아래에는 Sub-package가 하나도 없다. Production Class 1개(Application 진입점)와 이를 검증하는 Test 4개만 존재하며, 모두 같은 Package에 있다. `common`/`global`/`util`/`controller`/`service`/`repository` 같은 기술 또는 포괄 Package도 없다.

이 구조를 다른 형태로 재배치할 근거가 없다. Class가 1개뿐인 상태에서 미리 Layer나 기술 Package를 만들면 실제 코드보다 구조가 앞서게 되므로, 실제 Class가 추가되는 시점까지 현재 평평한 구조를 유지한다.

모든 Spring Bean은 Main Application Class(`GetiServerApplication`)가 있는 Root Package 아래에 위치해야 한다. `@SpringBootApplication`은 별도 `scanBasePackages` 없이 Application Class가 속한 Package를 기준으로 Component Scan을 수행하므로, Root Package 밖에 Bean을 두면 자동으로 탐지되지 않는다.

## 의존성 구성

| 항목 | 값 |
| --- | --- |
| Spring Modulith 버전 | `1.4.1` (Maven Central 최신 Stable/GA) |
| 적용 방식 | `dependencyManagement { imports { mavenBom(...) } }`으로 BOM 적용 (기존 `io.spring.dependency-management` Plugin 사용 방식과 동일) |
| 추가 Dependency | `testImplementation("org.springframework.modulith:spring-modulith-starter-test")` |
| Production Dependency | 없음 |

### 왜 Test 전용으로만 구성했는가

Production 코드는 아직 `@ApplicationModule`, Named Interface 등 Spring Modulith API를 실제로 사용하지 않는다(선언할 실제 도메인 Package가 없다). 구조 검증과 문서 생성은 Test에서만 필요하므로 `spring-modulith-starter-test`만 `testImplementation`으로 추가했고, Production Runtime에 불필요한 Dependency를 얹지 않았다. `spring-modulith-starter-test`는 `spring-modulith-test`, `spring-modulith-core`, `spring-modulith-docs`, ArchUnit을 Test Classpath에 Transitive로 포함한다.

향후 실제 도메인 Package를 만들고 Module 간 명시적 허용 Dependency(`@ApplicationModule(allowedDependencies = [...])`)나 Named Interface로 공개 API를 선언할 필요가 생기면, 그 시점에 Production Dependency 추가 여부를 다시 판단한다.

### 왜 1.4.1을 선택했는가 (Spring Boot 버전 호환성)

Spring Modulith 공식 Reference Documentation의 Compatibility Matrix 기준으로 `1.4.x`는 Spring Boot 3.5를 대상으로 컴파일/테스트된다. 이 프로젝트는 Spring Boot 4.1.0(Spring Framework 7.0.8)을 사용하므로 공식적으로 검증된 조합은 아니다.

실제 검증 시점 기준으로 Maven Central과 Spring Milestone 저장소에는 Spring Boot 4.x를 공식 대상으로 하는 Stable(GA) Spring Modulith 릴리스가 없었다(Spring Boot 4.x 대상은 아직 Milestone 단계). 이 프로젝트는 이미 detekt에서도 동일한 상황(Kotlin 2.3.21과 호환되는 Stable 릴리스가 없어 Alpha 버전을 실측 검증 후 채택, [`docs/development/code-quality.md`](../development/code-quality.md) 참고)을 겪었다.

이번에는 반대로 접근했다. Milestone/Pre-release를 앞당겨 쓰는 대신, 실제 Stable GA인 `1.4.1`을 이 프로젝트의 실제 Dependency 조합(Spring Boot 4.1.0, Gradle 9.5.1, Kotlin 2.3.21, Java Toolchain 25)에서 직접 빌드하고 실행해 검증했다.

- `./gradlew dependencies --configuration testRuntimeClasspath`로 확인한 결과, Gradle의 Spring Dependency Management가 `spring-modulith-core`/`spring-modulith-test`가 요구하는 `spring-core`/`spring-context`/`spring-test`/`spring-tx` `6.2.8`을 이 프로젝트가 관리하는 `7.0.8`로 강제 정렬했다(`6.2.8 -> 7.0.8`).
- `ApplicationModules.of(GetiServerApplication::class.java)` 생성, `modules.verify()`, `Documenter(modules).writeModulesAsPlantUml().writeModuleCanvases()` 실행을 모두 실제로 수행해 정상 동작(예외 없음, 정상적인 PlantUML 출력)을 확인했다.
- Spring Modulith의 핵심 기능(Package 스캔, ArchUnit 기반 구조 검증)은 ArchUnit이 Bytecode를 직접 분석하는 방식이라 Spring Framework Runtime 버전 자체에 크게 의존하지 않는다.

이 판단은 실측에 근거한 것이며, 공식적으로 보증된 조합은 아니다. Spring Modulith가 Spring Boot 4.1을 공식 지원하는 Stable 버전을 배포하면 재검토한다.

## 모듈 탐지 전략

Custom Detection Strategy를 별도로 구현하지 않고 Spring Modulith 기본 전략(Root Package의 Direct Subpackage를 Module로 인식)을 그대로 사용한다. 현재는 도메인 Package가 없어 이 전략을 바꿀 근거도, 예외를 선언할 대상도 없다.

## 기반 기술 Package 규칙 (Configuration / Infrastructure / Support)

특정 Domain에 속하지 않는 기술 기반 코드를 어디에 둘지는 실제 코드가 생기는 시점에 판단한다. 세 후보 모두를 미리 만들지 않는다.

| Package 후보 | 용도 | 현재 상태 |
| --- | --- | --- |
| `configuration` | Spring Framework Configuration Class, `@ConfigurationProperties` | 없음. Kotlin Class가 하나도 없다(Profile YAML 파일만 존재, [`configuration.md`](../development/configuration.md) 참고) |
| `infrastructure` | 실제 외부 시스템 Adapter(PostgreSQL, Redis, MinIO 등과 통신하는 코드) | 없음. PostgreSQL/Redis 연결 설정과 Migration/Test 기반은 구성했지만([`persistence.md`](../development/persistence.md)) 실제 Domain Entity/Repository/Adapter Class는 아직 없다 |
| `support` | 여러 Module이 실제로 공유하는 순수 기술 지원 코드(Clock Adapter, ID 생성기 등) | 없음. 공유가 필요한 코드 자체가 없다 |

`src/integrationTest/kotlin/team/inreok/getiserver/persistence/`에는 PostgreSQL/Redis Integration Test 전용 Entity/Repository(`PersistenceProbeEntity` 등, [`persistence.md`](../development/persistence.md) 참고)가 있다. 이는 `integrationTest`라는 별도 Gradle Source Set에만 존재하며 `main` Classpath에 포함되지 않으므로, `ApplicationModules.of(GetiServerApplication::class.java)` 탐지 대상이 아니고 Application Module로 세지 않는다. 위 표의 `infrastructure` Package(Production Adapter)와는 다른 목적이다.

이 세 Package는 각각 다음 조건을 만족하는 실제 Class가 생겼을 때만 만든다.

- `configuration`: 첫 `@Configuration` Class 또는 `@ConfigurationProperties` Class가 추가될 때. `config`, `configs`, `properties` 등 다른 이름과 혼용하지 않고 `configuration`으로 통일한다([`configuration.md`](../development/configuration.md)와 동일한 용어).
- `infrastructure`: 첫 외부 시스템 Adapter(예: PR 8의 Persistence Adapter)가 추가될 때. `infra`로 축약하지 않는다.
- `support`: 두 개 이상의 Module이 실제로 같은 기술 코드를 공유해야 하는 근거가 생겼을 때. 특정 Domain 전용 코드(Entity, DTO, Validator, Exception)는 이유를 막론하고 넣지 않는다.

Class가 한두 개뿐이라면 위 Package 아래에 다시 하위 Package(`configuration.properties` 등)를 만들지 않는다. 하위 Package는 같은 Package 안에서 관리하기 어려울 만큼 Class 수가 늘어났을 때 재검토한다.

Root Package(`team.inreok.getiserver`)는 Spring Modulith가 Direct Subpackage를 Module로 자동 탐지하므로, `configuration`/`infrastructure`/`support`를 Root Package 바로 아래에 추가하면 그 자체로 하나의 Application Module처럼 탐지된다. 이는 의도한 동작이다(기술 기반 코드를 하나의 논리적 경계로 보는 것). 여러 Domain Module이 이 Package에 실제로 의존하게 되면 `ModularityTest` 결과에서 의존 관계로 나타나므로, 그 시점에 이 판단이 여전히 타당한지 다시 확인한다.

## 향후 Module을 추가할 때의 원칙

아직 확정된 하위 Package 구조(`api`/`internal`, `domain`/`application` 등)를 강제하지 않는다. 다만 다음 원칙은 Module이 실제로 추가될 때부터 지킨다.

- 각 도메인 기능은 Root Package 바로 아래의 독립된 Package(Application Module 후보)로 구성한다. Package를 `controller`/`service`/`repository`처럼 기술 Layer 중심으로만 나누지 않는다.
- 다른 Module의 내부 구현 Package를 직접 참조하지 않는다. 다른 Module에 공개해야 하는 타입만 공개 API(또는 Spring Modulith Named Interface)로 노출한다.
- Module 간 순환 의존성을 만들지 않는다.
- Module 간 강한 결합이 필요 없다면 직접 참조 대신 Application Event를 검토한다(Event Publication Registry 등 영속 Event 인프라는 이번 범위가 아니다. [`docs/ai/testing-policy.md`](../ai/testing-policy.md), [`AGENTS.md`](../../AGENTS.md)의 제외 범위 참고).
- `common`/`global` 성격의 Package는 여러 Module이 실제로 공유하는 기술 요소(예: 공통 예외 타입, 공통 설정)만 담는다. 특정 도메인 전용 DTO, Validator, Exception을 편의상 넣지 않는다.
- 서로 다른 Module이 같은 JPA Entity를 직접 공유하지 않는다.
- Module 내부를 `presentation`/`application`/`domain`/`infrastructure`처럼 Layer로 다시 나눌지는 이 문서가 강제하지 않는다. Module 하나의 실제 Class 수와 책임이 그런 세분화를 정당화할 때 해당 Module PR에서 결정한다. Module 내부 상세 구조는 [`docs/ai/coding-conventions.md`](../ai/coding-conventions.md)의 "아직 확정되지 않은 규칙"에 남아 있다.

## 만들지 않는 Package

다음은 이 저장소에서 만들지 않는다. 편의를 위해 예외를 두지 않는다.

```text
Root 수준의 controller / service / repository / entity / dto (기술 Layer를 비즈니스 Module처럼 사용)
무제한 common / global / util / shared / core (책임이 불명확한 포괄 Package)
특정 Domain 전용 코드를 담은 공용 Package
아직 실제 Class가 없는 빈 Domain 또는 Layer Package
아직 필요하지 않은 미래 기능을 위한 Marker/Placeholder Class
다른 Module의 내부 구현을 직접 참조하는 코드
```

## Test Package 원칙

- Test Class는 검증 대상 Production Class와 같은 Package에 둔다. 이 저장소는 `src/test/kotlin/team/inreok/getiserver/`에 Production과 동일한 Root Package 구조를 그대로 사용한다.
- `ModularityTest`/`ModuleDocumentationTest`(구조 검증·문서 생성)처럼 특정 Production Class를 검증하지 않는 Architecture Test도 별도 `architecture`/`modularity` Package로 분리하지 않고 Root Package에 둔다. 이 Package는 Test Source에만 존재하며 Production Module로 탐지되지 않는다.
- Domain Module이 추가되면 그 Module의 Test도 같은 Module Package 안에 둔다. 모든 Test를 하나의 `test`나 `integration` Package로 모으지 않는다.
- 실제 공유 Fixture가 필요해지기 전에는 Test Support Package를 만들지 않는다.

## 구조 검증

```bash
./gradlew test --tests "*ModularityTest"
```

`ModularityTest`(`src/test/kotlin/team/inreok/getiserver/ModularityTest.kt`)는 `ApplicationModules.of(GetiServerApplication::class.java)`를 생성하고 `verify()`를 호출해 다음을 검증한다.

- Module 간 순환 의존성
- 다른 Module의 내부 구현(Non-public) 접근
- 명시적으로 선언한 허용 Dependency 위반

현재는 Module이 0개이므로 검증 대상도 없다(자명하게 통과). Module이 추가되면 이 Test가 실제 경계 위반을 잡아낸다. 일반 전체 테스트 실행에도 포함된다.

```bash
./gradlew test
```

## 모듈 구조 문서 생성

```bash
./gradlew test --tests "*ModuleDocumentationTest"
```

`ModuleDocumentationTest`(`src/test/kotlin/team/inreok/getiserver/ModuleDocumentationTest.kt`)는 `Documenter`로 PlantUML Component Diagram과 Module Canvas를 생성한다. 출력 경로는 실제 실행 결과로 확인했다.

```text
build/spring-modulith-docs/components.puml   전체 Module 관계 PlantUML
```

현재는 Module이 0개라 `components.puml`에 실질적인 Module Box나 관계선은 생성되지 않는다(Diagram 뼈대만 생성됨). Module Canvas(Module별 상세 문서)는 Module이 하나 이상 있어야 생성되므로 아직 생성되지 않는다. 이 Test는 일반 `test` 실행에 포함되며, 결과물은 `build/` 아래에만 생성되고 Git에 Commit하지 않는다(`.gitignore`의 기존 `build/` 규칙으로 충분하다).

## Runtime Verification

`spring.modulith.runtime.verification-enabled=true`를 운영 Application 시작 시 강제하지 않는다. 구조 검증은 현재 Test 단계(`ModularityTest`)에서만 수행한다. CI가 아직 없고, Startup 시간과 운영 영향을 검토하지 않은 상태에서 운영 환경에 Runtime Verification을 강제하는 것은 이번 범위가 아니다. CI 도입 이후 필요하면 재검토한다.

## Module Integration Test

`@ApplicationModuleTest`는 특정 Module과 그 Module이 선언한 협력 Module만 골라 Spring Context를 구성하는 Slice Test다. 일반 `@SpringBootTest`가 전체 Context를 올리는 것과 달리, 검증 대상 Module의 경계 안에서만 통합 동작을 확인할 때 사용한다.

현재는 실제 도메인 Module이 없어 `@ApplicationModuleTest`를 사용하는 빈 Test를 만들지 않았다. 실제 Module과 Spring Bean 협력이 생기는 시점에 해당 Module Package 안에 Integration Test를 추가한다.

## 아직 도입하지 않은 것

다음은 이번 PR 범위가 아니며, Persistence/Event Infrastructure/운영 환경이 구성된 이후 별도로 검토한다.

```text
spring-modulith-starter-jdbc / jpa / mongodb / neo4j / insight
spring-modulith-actuator
spring-modulith-observability
spring-modulith-events-* (Event Publication Registry)
Runtime Verification 강제 활성화
Module Integration Test (@ApplicationModuleTest)
jMolecules
별도 ArchUnit 직접 Dependency
```
