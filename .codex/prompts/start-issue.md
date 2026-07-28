# Prompt: Start Issue

지정된 GitHub Issue를 분석하고 안전하게 작업 Branch를 준비하기 위한 Prompt Template이다. 복사한 뒤 Placeholder를 실제 값으로 채워서 사용한다.

## Placeholder

```text
{ISSUE_NUMBER}
```

## 참조

먼저 [`AGENTS.md`](../../AGENTS.md)와 [`docs/ai/README.md`](../../docs/ai/README.md)를 확인한다. Sandbox/Approval 선택은 [`../policies/sandbox-policy.md`](../policies/sandbox-policy.md)를 따른다.

---

## Prompt 본문

```text
GETI-Server 저장소에서 Issue #{ISSUE_NUMBER} 작업을 시작할 준비를 해줘.

먼저 AGENTS.md와 docs/ai/README.md를 읽고 공통 규칙을 확인해.
그 다음 아래 순서로 진행해.

1. git status, 현재 Branch, git log --oneline --decorate -10 확인
   - 미커밋 변경이 있으면 삭제하거나 되돌리지 말고 출처를 분석해서 보고해.
2. gh issue view {ISSUE_NUMBER}로 Issue 제목, 배경, 작업 내용, 완료 조건, 제외 범위, Label을 확인해.
3. 선행 Issue나 PR이 언급되어 있으면 실제로 Merge/반영되었는지 확인해.
4. git switch develop && git pull --ff-only origin develop로 develop을 최신화해.
   - Fast-forward가 안 되면 임의로 강제하지 말고 원인을 보고해.
5. Issue 번호 기반 작업 Branch가 이미 있는지 확인해. 없으면 저장소 Git Convention(README.md, docs/ai/git-conventions.md)에 맞는 이름으로 새로 만들어.
6. 저장소에 실제 존재하는 Label만 확인해서, 상태 Label을 ready에서 in progress로 바꿔.
7. 구현 전 간단한 작업 계획을 세워.

이 단계에서는 코드 구현, Commit, Push, PR 생성은 하지 마. 여기까지만 수행해.

다음 상황이면 진행을 멈추고 원인을 보고해:
- 출처 불명의 미커밋 변경이 있음
- 선행 PR이 아직 반영되지 않음
- Issue를 찾을 수 없음
- 현재 Branch가 이미 다른 작업 중인 Branch임
- develop 최신화 실패
- GitHub 인증 실패

마지막에 다음을 보고해: Issue 번호와 제목, 완료 조건, 제외 범위, Base Branch, 작업 Branch, Label 변경 내역, 구현 계획, 중단되었다면 원인과 위험 요소.
```
