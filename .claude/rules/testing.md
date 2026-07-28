# Testing (Claude Code)

Claude Code가 GETI-Server에서 테스트를 작성하고 실행할 때 따르는 규칙이다. 정책의 배경과 상세 원칙은 [`docs/ai/testing-policy.md`](../../docs/ai/testing-policy.md)를 따르고, 이 문서는 Claude Code가 실제로 취해야 할 행동을 다룬다.

## 테스트 탐색

구현 전에 다음을 확인한다.

- 관련 기존 Test (`src/test/kotlin/...`)
- Test Naming과 Test Package 구조
- 사용 중인 Test Framework(JUnit 5, Spring Boot Test)
- Mock 사용 방식
- Spring Context Test 여부
- 기존 Fixture 또는 Helper

## 테스트 작성

- 변경한 동작을 검증하는 테스트를 작성하거나 갱신한다.
- 정상 경로와 의미 있는 예외 경로를 검토한다.
- 구현 세부사항보다 외부에서 관찰 가능한 동작을 우선 검증한다.
- 의미 없는 Getter/Setter Test를 생성하지 않는다.
- 실제 동작을 검증하지 않는 단순 `not null` 수준의 Test를 남발하지 않는다.
- 테스트를 통과시키기 위해 Production Code의 동작을 왜곡하지 않는다.
- 기존 Test의 Naming과 언어(한국어/영어) 관례를 우선한다.

## 테스트 우회 금지

다음 방식을 금지한다.

```text
기존 Test 삭제
@Disabled 추가
실패 Assertion 제거
예외를 무조건 Catch
조건문으로 Test 환경만 우회
Test 순서에 의존
실패 Test를 실행 대상에서 제외
```

## 테스트 실행

변경 범위에 맞는 Test를 먼저 실행한다.

```bash
./gradlew test
```

마지막에 전체 검증을 실행한다.

```bash
./gradlew clean test build
```

Windows에서는 `.\gradlew.bat`를 사용한다.

## 실패 분류

테스트나 Build가 실패하면 원인을 다음처럼 구분해서 보고한다.

```text
컴파일 실패
테스트 실패
Spring Context 실패
설정 실패
Dependency 실패
외부 서비스 실패
로컬 환경 실패
권한 실패
```

환경 문제를 코드 문제처럼 수정하지 않는다. 코드 문제를 환경 문제로 돌리지 않는다.

## 결과 보고

다음을 기록한다.

- 실행한 명령
- 성공한 Test
- 실패한 Test
- Build 결과
- 실행하지 못한 검증
- 실패 원인
- 남은 위험

## 아직 도입되지 않은 도구

다음 테스트 도구는 이 저장소에 아직 도입되지 않았다. 관련 작업이 아니라면 필수로 요구하거나 임의로 도입하지 않는다.

```text
Testcontainers
ArchUnit
Mutation Testing
Contract Test
```
