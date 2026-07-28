# Prompt: Review Code

현재 Branch의 변경 사항을 코드 수정 없이 리뷰하기 위한 Prompt Template이다. `codex review` 전용 명령으로 직접 실행할 수도 있다.

## 참조

[`AGENTS.md`](../../AGENTS.md)를 참고한다.

---

## 전용 명령으로 실행 (권장)

```bash
codex review --base develop
codex review --uncommitted
```

## Prompt 본문 (대화형/`codex exec`로 실행할 경우)

```text
GETI-Server 저장소의 현재 Branch 변경 사항을 코드 수정 없이 리뷰해줘.

비교 대상은 기본적으로 다음이야:
git diff origin/develop...HEAD

먼저 AGENTS.md를 확인한 뒤, 다음 항목을 기준으로 검토해.

- Issue 요구사항 충족 여부
- Issue 제외 범위 준수 여부
- 논리 오류
- Null 및 예외 처리
- 입력 검증
- 인증 및 인가
- 데이터 정합성
- Transaction 범위
- 동시성
- API 호환성
- 성능 (N+1, 불필요한 반복, Blocking 호출 등)
- 보안 (Secret 노출, 권한 상승 가능성)
- 기존 Pattern과의 일관성
- 불필요한 Dependency
- 테스트 누락
- 관련 없는 변경 포함 여부
- 남아 있는 Debug Code

발견 사항마다 중요도(Critical/High/Medium/Low/Suggestion), 파일과 위치, 문제, 영향, 수정 방향, 근거를 포함해서 보고해.

내가 수정까지 명시적으로 요청하지 않는 한 코드를 변경하지 마.

문제가 없더라도 검토한 범위, 실행한 명령, 검토하지 못한 영역, 잔여 위험을 보고해.
```
