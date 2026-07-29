---
name: test-and-verify
description: 변경 유형(문서, 설정, 로직, API, DB)에 맞는 테스트와 검증을 선택하고 결과를 정확하게 판정하는 기준을 다룬다.
---

# Test and Verify

GETI-Server에서 변경 사항을 검증할 때 참고하는 상세 기준이다. [`implement`](../../commands/implement.md), [`fix-bug`](../../commands/fix-bug.md), [`verify`](../../commands/verify.md), [`prepare-pr`](../../commands/prepare-pr.md) Command가 이 Skill을 참조한다.

## 변경 유형별 검증

### 문서 변경

- Markdown 문법과 Code Fence가 닫혀 있는지
- 상대 링크 대상이 실제로 존재하는지
- 파일명 대소문자가 일치하는지
- Build에 영향이 없는지 (문서만 변경했어도 `clean test build`는 실행한다)

### 설정 변경 (`build.gradle.kts`, `application.yaml` 등)

- Parsing이 성공하는지
- Profile에 영향이 있는지 (현재 저장소는 별도 Profile 체계가 없다)
- 관련 Bean이 정상 생성되는지
- Spring Context가 정상 로드되는지
- Build 성공 여부

### 비즈니스 로직 변경

- 정상 경로
- 경계값
- 예외 경로
- 권한이 관련되면 권한 분기
- 데이터 정합성에 영향이 있는지

이 저장소는 아직 비즈니스 로직이 없으므로, 관련 Issue가 생기면 이 기준을 적용한다.

### API 변경

- Request 처리
- Response 형식
- Validation
- Status Code
- 기존 호출자와의 호환성

### DB 변경

- Migration
- Schema
- Constraint
- Rollback 가능성
- Test에서 사용할 Database (현재는 `testRuntimeOnly("com.h2database:h2")`로 외부 인프라 없이 Context Test를 실행한다)

이 저장소에는 아직 DB Migration이나 API 구조가 없으므로, 해당 변경이 생기기 전까지는 이 절만 일반 기준으로 참고한다.

## Test 선택

- 가장 작은 관련 Test부터 실행한다 (`./gradlew test`).
- 영향 범위가 넓으면 전체 Test를 실행한다.
- Kotlin 코드를 변경했다면 `./gradlew spotlessCheck`(포맷)와 `./gradlew detekt`(정적 분석)도 확인한다. 포맷 위반은 `./gradlew spotlessApply`로 정리한다.
- 마지막에 항상 `./gradlew clean test build`로 마무리한다. `check`에 `spotlessCheck`, `detekt`, `koverVerify`가 이미 포함되어 있어 별도로 반복 실행할 필요는 없다.
- 커버리지 수치 확인이 필요하면 `./gradlew koverHtmlReport` 또는 `./gradlew koverXmlReport`를 별도로 실행한다(`check`에는 포함되지 않는다).
- 이미 통과가 확인된 범위를 불필요하게 반복 실행하지 않는다.
- 실행하지 못한 검증은 숨기지 않고 명시한다.

## 실패 분석

- 로그의 첫 번째 실제 원인(Root Cause)을 확인한다. 연쇄적으로 발생한 후속 실패와 근본 실패를 구분한다.
- 환경 문제(도구 미설치, 권한 등)와 코드 문제를 구분한다.
- Stack Trace 일부만 보고 성급하게 결론 내리지 않는다.

실패 유형 분류:

```text
컴파일 실패
테스트 실패
Spring Context 실패
설정 실패
Dependency 실패
포맷 위반 (spotlessCheck)
정적 분석 위반 (detekt)
문서 또는 경로 실패
외부 서비스 실패
로컬 환경 실패
권한 실패
```

## 완료 기준

다음을 모두 만족해야 "완료"로 표현한다 ([`docs/ai/completion-policy.md`](../../../docs/ai/completion-policy.md) 참고).

- 요구사항 충족
- 관련 Test 성공
- 전체 Build 성공
- Diff 직접 검토
- Secret 미포함 확인
- 실행하지 못한 검증 명시
