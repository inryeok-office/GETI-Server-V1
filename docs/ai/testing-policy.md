# 테스트 및 검증 정책 (AI 작업 원칙)

현재 GETI-Server는 JUnit 5(JUnit Platform)와 Spring Boot Test를 사용하며, Application Context 테스트(`GetiServerApplicationTests`)만 존재하는 초기 단계다. Context 테스트는 외부 PostgreSQL 없이도 통과하도록 테스트 전용 H2 In-Memory Database(`testRuntimeOnly`)를 사용한다. 코드 커버리지는 Kover로 측정한다. 테스트 유형별 정책, 도구 선정 이유, 커버리지 명령은 [`docs/development/testing.md`](../development/testing.md)를 따른다.

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

## 아직 필수로 요구하지 않는 도구

다음 테스트 도구는 이 저장소에 아직 도입되지 않았다. 관련 작업이 아니라면 필수로 요구하거나 임의로 도입하지 않는다.

```text
Testcontainers
ArchUnit
Mutation Testing
Contract Test
MockK
kotlinx-coroutines-test
```

`MockK`는 실제 Mocking이 필요한 Kotlin Service/Slice Test가 생기는 시점에 도입 여부를 재검토한다(Mockito는 Spring Boot Test Starter를 통해 이미 사용 가능하다). `kotlinx-coroutines-test`는 Production Source가 Coroutine을 사용하기 시작하면 도입을 검토한다. 위 도구는 필요에 따라 후속 PR에서 도입되고, 이 문서도 함께 갱신될 예정이다.
