# 테스트 및 코드 커버리지

GETI-Server는 개발자와 AI Agent가 동일한 방식으로 테스트를 작성하고 실행할 수 있도록 다음 환경을 사용한다.

## 적용 도구

| 도구 | 버전 | 역할 |
| --- | --- | --- |
| JUnit Platform / JUnit Jupiter | 6.0.3 (Spring Boot Dependency Management 관리) | Test Engine, Test 실행 |
| Spring Boot Test (`spring-boot-starter-data-jpa-test`, `spring-boot-starter-webmvc-test`) | Spring Boot 4.1.0 관리 | Spring Context/Slice Test 지원 |
| AssertJ | 3.27.7 (위 Test Starter의 Transitive Dependency) | Assertion |
| Mockito, Mockito JUnit Jupiter | 5.23.0 (위 Test Starter의 Transitive Dependency) | Mocking (현재 실사용 Test 없음) |
| kotlin-test-junit5 | 2.3.21 | Kotlin JUnit 5 연동 |
| H2 Database (`testRuntimeOnly`) | 2.4.240 | `@SpringBootTest`가 운영 PostgreSQL 없이 JPA Context를 구성하기 위한 In-memory DB |
| Kover (`org.jetbrains.kotlinx.kover`) | 0.9.9 | Kotlin/JVM 코드 커버리지 Report |

`spring-boot-starter-data-jpa-test`/`spring-boot-starter-webmvc-test`는 기존에 이미 선언되어 있었고, AssertJ와 Mockito는 이 Starter가 Transitive하게 제공한다(`./gradlew dependencies --configuration testRuntimeClasspath`로 확인). 이번 작업에서 별도로 직접 선언하지 않았다.

## 도입하지 않은 도구

| 도구 | 도입하지 않은 이유 |
| --- | --- |
| MockK | 현재 프로젝트에 Mocking이 필요한 Kotlin Service/Slice Test가 없다. Mockito(Mockito 5의 Inline Mock Maker)가 Test Starter를 통해 이미 제공되고 있어, 실제 Mocking이 필요한 테스트가 생기는 시점에 Mockito 유지와 MockK 도입 여부를 다시 판단한다. |
| kotlinx-coroutines-test | Production Source에 `suspend` 함수, `Flow`, Coroutine Dispatcher 등 Coroutine 사용이 없다(`spring-boot-starter-webmvc` 기반 Servlet MVC이며 WebFlux가 아니다). |
| Kotest | 기존 테스트가 JUnit Jupiter 기반이고, Kotest 전용 기능이 필요한 요구사항이 없다. Test Engine을 중복으로 늘리지 않는다. |
| JUnit Vintage Engine | JUnit 4 기반 기존 테스트가 없다. |
| Testcontainers, 실제 PostgreSQL/Redis 연결 테스트 | Docker 및 Persistence 기반이 아직 구성되지 않았다. Docker 기반이 마련된 이후 별도로 검토한다. |

Spring Boot 4.1(Spring Framework 7)에서는 `@MockBean`/`@SpyBean`이 제공되지 않고 `org.springframework.test.context.bean.override.mockito.MockitoBean`/`MockitoSpyBean`이 제공된다(`spring-test` 7.0.8 Jar로 확인). 이번 PR은 Mock이 필요한 Slice Test를 추가하지 않으므로 예제 코드는 작성하지 않았으며, 향후 Controller/Repository Slice Test를 추가할 때 위 Annotation을 사용한다.

## 전체 테스트 실행

```bash
./gradlew test
```

Windows:

```powershell
.\gradlew.bat test
```

`useJUnitPlatform()`은 `build.gradle.kts`의 `tasks.withType<Test>`에 이미 구성되어 있다.

## 전체 품질 검사

```bash
./gradlew check
```

`check`는 `spotlessCheck`, `detekt`, `test`, `koverVerify`를 포함한다. 현재 `koverVerify`에는 별도 최소 기준(Rule)을 설정하지 않아 항상 통과하는 상태다(아래 [Coverage Gate](#coverage-gate) 참고).

## 전체 Build 검증

```bash
./gradlew clean test build
```

## 포맷 및 정적 분석

```bash
./gradlew spotlessCheck
./gradlew detekt
```

도구별 세부 설정은 [`code-quality.md`](./code-quality.md)를 따른다.

## 커버리지 Report

```bash
./gradlew koverHtmlReport
./gradlew koverXmlReport
```

Report 위치:

```text
build/reports/kover/html/index.html   HTML Report
build/reports/kover/report.xml        XML Report
```

콘솔에서 바로 요약을 보려면:

```bash
./gradlew koverLog
```

### Filter

현재 Production Source가 `GetiServerApplication`(Application Entry Point) 하나뿐이라 별도의 Kover Filter(Class/Package 제외)를 설정하지 않았다. Controller, Service, Domain 등 실제 계층이 추가되어도 해당 계층을 Filter로 광범위하게 제외하지 않는다.

### Coverage Gate

`koverVerify`에 최소 Line/Branch 기준(Rule)을 아직 설정하지 않았다. 현재 테스트는 Application Context Smoke Test 하나뿐이라 임의의 기준(예: Line 80%)을 설정할 근거가 없다. 기능 구현과 테스트가 누적된 뒤, 실제 측정값을 근거로 CI 관련 후속 작업에서 기준 도입 여부를 재검토한다.

### 현재 측정값 (참고용, 커밋 시점 스냅샷)

`GetiServerApplicationTests.contextLoads()` 실행 기준:

- Line Coverage: 50% (2개 중 1개) — `GetiServerApplication` 생성자는 Spring Context 기동으로 Covered, `main()` 함수는 Test에서 호출되지 않아 Uncovered.
- Class Coverage: 50% (2개 중 1개)

## 테스트 유형

### 순수 단위 테스트

Domain Logic, Service Logic, Mapper, Validator 등 대상. Spring Context를 시작하지 않고 빠르고 독립적으로 실행한다. 현재 프로젝트에는 아직 해당 계층이 없다.

### Spring Slice Test

Controller, Repository, Configuration Binding 등 특정 계층만 필요한 Context로 로드한다(`@WebMvcTest`, `@DataJpaTest` 등). 전체 `@SpringBootTest`를 기본값으로 사용하지 않는다. 현재 프로젝트에는 아직 해당 계층이 없다.

### Spring 통합 테스트

여러 Spring Component의 실제 협력이나 전체 Application Context 조합이 검증 대상일 때만 사용한다. 외부 운영 인프라(운영 DB, 운영 Redis 등)에 연결하지 않는다.

### Smoke Test

`GetiServerApplicationTests.contextLoads()`가 여기에 해당한다. H2 In-memory DB로 운영 PostgreSQL 없이 Application Context가 정상 기동하는지 검증한다.

## 테스트 작성 원칙

- 독립성: 테스트는 실행 순서와 다른 테스트 결과에 의존하지 않는다.
- 결정성: 현재 시간, Random 값, 외부 네트워크에 의존하지 않는다. `Thread.sleep`을 사용하지 않는다.
- 외부 인프라 미사용: 운영 DB, 운영 Redis, 외부 API를 실제로 호출하지 않는다.
- 명확한 Assertion: 코드만 실행하고 검증하지 않는 테스트를 만들지 않는다. Application Context Smoke Test처럼 Context 기동 자체가 검증 대상인 경우는 예외로 한다.
- Spring Context 최소화: 순수 로직은 Spring Context 없이 테스트하고, Slice Test는 필요한 계층만 로드한다.
- Mock 남용 금지: 모든 협력 객체를 무조건 Mock 처리하거나, 호출 횟수/순서 검증에 과도하게 의존하지 않는다. Private 함수를 직접 테스트하지 않는다.
- 커버리지를 위한 무의미한 테스트를 작성하지 않는다.
- Naming: 함수명은 검증 대상, 조건, 기대 결과를 표현한다. Backtick 함수명(예: `` `유효한 요청이면 사용자를 생성한다`() ``)을 기존 관례로 사용한다.

## CI 계획

- 현재는 로컬에서 실행하는 `test`, `koverHtmlReport`, `koverXmlReport`, `check` Gradle Task만 제공한다.
- GitHub Actions CI는 이번 PR 범위가 아니며, 프로젝트 기반 구성이 완료된 뒤 DevOps 담당자가 별도 PR에서 구성한다. CI에서는 이 문서가 정의한 Task를 그대로 사용할 예정이다.
- CD는 배포 서버와 운영 환경이 확보된 이후 별도로 구성한다.
