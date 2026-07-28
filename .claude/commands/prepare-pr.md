---
description: 현재 작업을 검증하고 Commit, Push, develop 대상 Draft PR까지 준비한다 (사용자 명시적 요청 시에만)
---

## 목적

현재 작업을 검증한 뒤 Commit, Push, Draft Pull Request 생성까지 진행한다.

**이 Command는 사용자가 Commit, Push, PR 생성을 명시적으로 요청한 경우에만 실행한다.**

## 참조

상세 기준은 [`pull-request` Skill](../skills/pull-request/SKILL.md)과 [`test-and-verify` Skill](../skills/test-and-verify/SKILL.md)을 따른다.

## 수행 절차

1. [`AGENTS.md`](../../AGENTS.md), [`CLAUDE.md`](../../CLAUDE.md), 현재 Issue를 확인한다.
2. 현재 Branch를 확인한다.
3. `gh pr list --head <branch>`로 동일 Head Branch의 기존 PR을 확인한다. 있으면 새로 만들지 않고 기존 PR을 갱신한다.
4. `git status`, `git diff`로 Working Tree를 확인한다.
5. Issue 요구사항과 제외 범위를 대조한다.
6. 관련 Test를 실행한다.
7. 전체 Test와 Build를 실행한다.
8. `git diff --check`를 실행한다.
9. Secret과 불필요한 파일이 포함되지 않았는지 확인한다.
10. 관련된 파일만 Stage한다.
11. Commit 메시지를 작성한다 (`<type>: <한글 작업 내용>`).
12. Commit한다.
13. Push한다 (Force Push 금지).
14. `develop`을 대상으로 Draft PR을 생성한다 (기존 PR이 있으면 본문을 갱신한다).
15. PR 본문에 관련 Issue를 `Closes #{issue-number}`로 연결한다.
16. 저장소에 실제 존재하는 Label을 PR에 적용한다.
17. Issue 상태 Label을 `review`로 변경한다.
18. 결과를 보고한다.

## PR 본문 기본 구조

```markdown
## 작업 배경

## 변경 내용

## 주요 설계 판단

## 검증

## 영향 범위

## 제외 범위

## 체크리스트

- [ ] 현재 Issue의 요구사항을 충족합니다.
- [ ] Issue 제외 범위를 준수했습니다.
- [ ] 관련 없는 변경을 포함하지 않았습니다.
- [ ] Commit 메시지가 `<type>: <한글 작업 내용>` 형식을 따릅니다.
- [ ] 관련 Test를 실행했습니다.
- [ ] 전체 Test와 Build 결과를 확인했습니다.
- [ ] Secret 또는 민감정보가 포함되지 않았습니다.
- [ ] 문서 갱신 필요성을 검토했습니다.

## 관련 Issue

Closes #이슈번호
```

실제로 검증한 항목만 체크한다.

## 금지 사항

- 사용자 요청 없이 Commit, Push, PR 생성
- Force Push
- 중복 PR 생성
- 사용자 요청 없는 Merge
- 검증하지 않은 항목을 체크

## 결과 보고

- 검증 결과 (Test, Build)
- Commit hash
- Push 결과
- PR 번호와 URL, base/head, Draft 여부
- Issue Label 변경
- 남은 작업
