# Prompt: Fix Bug

버그를 재현하고 근본 원인을 확인한 뒤 최소 범위로 수정하기 위한 Prompt Template이다.

## Placeholder

```text
{ISSUE_NUMBER}
{BUG_DESCRIPTION}
{EXPECTED_BEHAVIOR}
```

## 참조

[`AGENTS.md`](../../AGENTS.md), [`../policies/execution-policy.md`](../policies/execution-policy.md)를 참고한다.

---

## Prompt 본문

```text
GETI-Server 저장소, Issue #{ISSUE_NUMBER} 작업 Branch에서 다음 버그를 수정해줘.

버그 증상: {BUG_DESCRIPTION}
기대 동작: {EXPECTED_BEHAVIOR}

먼저 AGENTS.md를 확인하고, 아래 순서로 진행해.

1. 증상과 기대 동작을 정리해.
2. 관련 로그나 Stack Trace가 있으면 분석해.
3. 관련 코드와 기존 Test를 탐색해.
4. 재현 가능 여부를 확인해.
5. 가능하면 실패를 재현하는 Test를 먼저 작성해.
6. 가능한 원인 목록을 세우고, 로그와 코드로 실제 원인을 검증해.
7. 최소 범위로 수정해.
8. 회귀 Test를 실행해.
9. 전체 Test와 Build를 실행해 (./gradlew clean test build).
10. Diff를 직접 리뷰해.

하지 말아야 할 것:
- 증상만 숨기는 수정
- 예외를 무조건 Catch해서 무시
- 로그만 삭제하고 해결 처리
- Validation 제거
- Security 우회
- 실패 Assertion 삭제
- Test 비활성화 (@Disabled 등)
- 재현 없이 추측만으로 대규모 수정
- 무관한 Refactoring 포함

재현이 안 되면 추측으로 큰 변경을 하지 말고, 재현 시도 결과와 막힌 지점을 보고해.

마지막에 다음을 보고해: 재현 여부, 실제 원인, 수정 내용, 회귀 방지 Test, 검증 결과, 남은 위험.
```
