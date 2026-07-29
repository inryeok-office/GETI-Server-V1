---
description: 버그를 재현하고 실제 원인을 찾아 수정한다
argument-hint: <버그 증상 또는 재현 조건>
---

## 목적

보고된 버그(`$ARGUMENTS`)를 재현하고, 증상을 숨기는 대신 실제 원인을 찾아 최소 범위로 수정한다.

## 참조

상세 기준은 [`spring-boot-change` Skill](../skills/spring-boot-change/SKILL.md)과 [`test-and-verify` Skill](../skills/test-and-verify/SKILL.md)을 따른다.

## 필요 정보

- 버그 증상
- 재현 조건
- 기대 동작
- 가능한 경우 로그 또는 Stack Trace

정보가 부족하면 추측으로 진행하지 않고 사용자에게 확인한다.

## 수행 절차

1. 증상과 기대 동작을 정리한다.
2. 재현 가능 여부를 확인한다.
3. 관련 코드와 기존 Test를 탐색한다.
4. 가능한 원인 목록을 작성한다.
5. 로그와 코드로 실제 원인을 검증한다.
6. 가능하면 실패를 재현하는 Test를 먼저 작성한다.
7. 최소 범위로 수정한다.
8. 회귀 Test를 실행한다.
9. Kotlin 코드를 변경했다면 `./gradlew spotlessApply`와 `./gradlew detekt`로 포맷/정적 분석을 확인한다.
10. 전체 Test와 Build를 실행한다 (`check`에 `spotlessCheck`, `detekt`, `koverVerify`가 포함됨).
11. Diff를 직접 리뷰한다.
12. 원인과 해결 방식을 보고한다.

## 금지 사항

- 증상만 숨기는 수정
- 예외를 무조건 Catch하여 무시
- 로그만 삭제하고 해결 처리
- Validation 제거
- Security 우회
- 실패 Assertion 삭제
- Test 비활성화(`@Disabled` 등)
- 재현 없이 추측만으로 대규모 수정
- 무관한 Refactoring 포함

## 결과 보고

- 재현 여부
- 실제 원인
- 수정 내용
- 회귀 방지 Test
- 검증 결과
- 남은 위험
