# 테스트 및 검증 정책 (AI 작업 원칙)

현재 GETI-Server는 JUnit 5(JUnit Platform)와 Spring Boot Test를 사용한다. `src/test`의 Unit/Slice Test는 실제 PostgreSQL/Redis 없이 통과해야 하며, JPA Context 테스트는 테스트 전용 H2 In-Memory Database(`testRuntimeOnly`)를 사용한다. Docker(Testcontainers)가 필요한 PostgreSQL/Redis Persistence Integration Test는 별도 `integrationTest` Gradle Task로 분리되어 있으며 `test`/`check`/`build`가 의존하지 않는다([`docs/development/persistence.md`](../development/persistence.md) 참고). Web/Controller 계층은 `@WebMvcTest` + `MockMvc` 기반 Slice Test로 검증한다([`docs/development/web-api.md`](../development/web-api.md) 참고). 코드 커버리지는 Kover로 측정한다. 테스트 유형별 정책, 도구 선정 이유, 커버리지 명령은 [`docs/development/testing.md`](../development/testing.md)를 따른다.

## 원칙

- 변경한 동작에 대응하는 테스트를 작성하거나 갱신한다. 기능 변경 없이 테스트만 추가하는 작업도 동일한 기준을 따른다.
- 기존에 통과하던 테스트를 근거 없이 삭제하거나 수정하지 않는다.
- 정상 경로뿐 아니라 의미 있는 예외/실패 경로도 함께 검토한다. 다만 억지로 모든 경우의 수를 테스트로 만들지는 않는다.
- 테스트가 실패하면 원인을 분석한다. 코드 문제인지, 테스트 자체의 문제인지, 로컬 환경 문제인지 구분해서 보고한다.
- 테스트를 통과시키기 위해 테스트를 삭제하거나 비활성화하지 않는다.
- `@Disabled`로 실패하는 테스트를 건너뛰어 우회하지 않는다. 정말 비활성화가 필요하다면 사유를 명시하고 사용자에게 확인받는다.
- 관련 테스트를 먼저 실행하고, 마지막에 전체 Build(`clean test build`)로 검증한다.
- 환경 문제(예: 로컬에 필요한 도구 미설치)와 코드 문제를 구분해서 보고하고, 실행한 명령과 결과를 기록한다.
- 의미 없이 컴파일만 통과시키는 자동 생성 테스트(빈 assertion 등)를 만들지 않는다.
- 내부 구현 세부사항에 지나치게 결합된 테스트(private 필드 직접 접근, 내부 순서에 의존하는 Mock 검증 등)를 피하고, 공개된 동작을 기준으로 검증한다.
- 시간, 환경, 실행 순서에 따라 결과가 달라지는 비결정적 테스트를 만들지 않는다.
- 실행하지 못한 테스트나 검증 항목은 완료 보고에 명확히 남긴다 ([`completion-policy.md`](./completion-policy.md) 참고).
- `src/test`(Unit/Slice Test)는 실제 PostgreSQL/Redis 실행을 전제로 작성하지 않는다. PostgreSQL/Redis가 실제로 필요한 검증은 `src/integrationTest`에 Testcontainers로 작성한다.
- Test에서 실제 외부 API(사람인, 고용24, Discord, OAuth Provider 등)를 호출하지 않는다. 외부 연동은 Mock/Stub 또는 WireMock 같은 Test Double로 검증한다.
- Persistence(JPA/Flyway/Redis) 관련 코드를 변경하면 `./gradlew test`뿐 아니라 `./gradlew integrationTest`(Docker 필요), `./gradlew test --tests "*ModularityTest"`, 전체 Build(`clean test build`)까지 함께 확인한다.
- 새 Controller나 공통 Web 기반(응답 형식, 전역 예외 처리, CORS 등)을 추가하거나 변경하면 `@WebMvcTest` 기반 Web Slice Test와 오류 응답 Contract Test(Field 이름, HTTP Status, Error Code, 내부 정보 미노출)를 함께 작성하거나 갱신한다. Production Source에는 예시/Test 전용 Controller를 두지 않는다(`src/test`에만 둔다).

## 기본 검증 명령

Windows:

```powershell
.\gradlew.bat clean test build
```

Unix 또는 Git Bash:

```bash
./gradlew clean test build
```

`clean test build`는 `check` Lifecycle을 통해 `spotlessCheck`(포맷 검사), `detekt`(정적 분석), `koverVerify`(커버리지 검증, 현재 기준 미설정으로 항상 통과)도 함께 실행한다. 포맷 위반이 있으면 `./gradlew spotlessApply`로 먼저 자동 정리한 뒤 재검증한다. 도구별 역할과 설정은 [`docs/development/code-quality.md`](../development/code-quality.md)와 [`docs/development/testing.md`](../development/testing.md)를 따른다.

커버리지 Report가 필요하면 `./gradlew koverHtmlReport`, `./gradlew koverXmlReport`를 실행한다. 이 Task는 `check`에 포함되어 있지 않으므로 필요할 때 별도로 실행한다.

GitHub Actions CI가 Pull Request마다 이 명령들을 자동으로 재실행한다([`docs/development/ci.md`](../development/ci.md) 참고). 로컬 검증은 CI 실행 전에 실패를 미리 잡기 위한 것이며, CI 실행 결과를 대체하지 않는다.

## 아직 필수로 요구하지 않는 도구

다음 테스트 도구는 이 저장소에 아직 도입되지 않았다. 관련 작업이 아니라면 필수로 요구하거나 임의로 도입하지 않는다.

```text
ArchUnit
Mutation Testing
Contract Test
REST Assured
kotlinx-coroutines-test
```

Testcontainers는 `integrationTest` Source Set(PostgreSQL/Redis Persistence Integration Test)에 한해 도입되어 있다([`docs/development/persistence.md`](../development/persistence.md)). 그 외 목적(예: 다른 외부 시스템 Integration Test)으로 확대할지는 실제 필요가 생기는 시점에 판단한다.

GETI Notion Tech Stack은 Mocking 도구로 `Mockito`(MockK 아님)를 확정했다. Mockito는 별도 Dependency 없이 `spring-boot-starter-webmvc-test`/`spring-boot-starter-data-jpa-test`를 통해 이미 Test Classpath에서 사용할 수 있다. `ArchUnit`도 Notion이 확정 도구로 명시하지만, Spring Modulith `ModularityTest`가 이미 Module 경계를 검증하고 있고 실제 검증할 교차 참조 규칙은 Domain Module이 2개 이상 생겨야 의미가 있어 지금 추가하지 않았다. `kotlinx-coroutines-test`는 Production Source가 Coroutine을 사용하기 시작하면 도입을 검토한다. 위 도구는 필요에 따라 후속 PR에서 도입되고, 이 문서도 함께 갱신될 예정이다.
