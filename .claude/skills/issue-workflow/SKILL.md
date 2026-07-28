---
name: issue-workflow
description: GitHub Issue 기반 작업의 시작부터 PR 연결까지 일관된 절차를 제공한다. Issue 분석, Branch, 상태 Label 흐름, 진행 댓글, 실패 처리 기준을 다룬다.
---

# Issue Workflow

GETI-Server에서 GitHub Issue 기반 작업을 시작하고 진행할 때 참고하는 상세 판단 기준이다. [`start-issue` Command](../../commands/start-issue.md)가 이 Skill을 참조한다.

## Issue 분석

Issue를 확인할 때 다음을 모두 읽는다.

- 제목
- 배경
- 작업 내용 (체크리스트)
- 완료 조건 (Acceptance Criteria)
- 제외 범위
- 선행 Issue와 PR (본문이나 댓글에서 언급되었는지)
- Label (작업 유형, 우선순위, 영향 영역)
- 담당 범위 (Assignee가 있다면 충돌 여부)

완료 조건과 제외 범위를 확인하지 않고 작업 범위를 임의로 판단하지 않는다.

## Branch

- Base Branch는 기본적으로 `develop`이다. 작업 전 `git pull --ff-only origin develop`로 최신화한다.
- Branch 이름에 Issue 번호를 포함한다: `<type>/{issue-number}-{short-description}`.
- 이미 해당 Issue 번호의 작업 Branch가 로컬 또는 원격에 있는지 확인하고, 있으면 중복 생성하지 않고 그 Branch를 사용한다.
- `main`, `develop`에서 직접 작업하지 않는다.

## 상태 Label 흐름

실제 저장소에 존재하는 상태 Label(`gh label list`로 확인)을 기준으로 다음 흐름을 따른다.

```text
📋 backlog → 📝 ready → 🚧 in progress → 👀 review → (Issue Close)
                                   ↕
                              ⛔ blocked
```

- 작업을 시작하면 `ready`를 제거하고 `in progress`를 추가한다.
- 구현과 검증이 끝나 리뷰를 기다리면 `in progress`를 제거하고 `review`를 추가한다.
- 외부 요인으로 막히면 `blocked`를 추가하고, 해결되면 이전 상태로 되돌린다.
- 상태 Label은 항상 하나만 유지한다.
- `✅ done` Label은 이 저장소에 존재하지 않는다. Issue Close 상태 자체가 완료를 의미한다 ([`docs/ai/git-conventions.md`](../../../docs/ai/git-conventions.md) 참고).

## 진행 댓글

다음 시점에 Issue 댓글을 남길 수 있다.

- 작업 시작
- 주요 단계 완료 (예: 여러 Commit으로 나뉜 작업의 중간 지점)
- Blocked 상태 진입
- PR 생성
- 검증 실패로 방향 전환이 필요할 때
- 작업 범위 변경이 필요할 때

댓글에는 실제로 수행한 결과, 실제 Commit hash, 실제 Test/Build 결과만 작성한다. 사소한 중간 상태까지 매번 댓글을 남기지 않는다.

## 실패 처리

다음 상황에서는 임의로 진행하지 않고 원인을 보고한다.

- GitHub 인증 실패 (`gh auth status`로 확인)
- Issue 조회 실패 (잘못된 번호, 권한 없음)
- 필요한 Label이 저장소에 없음 — 임의로 만들지 않고 실제 존재하는 Label로 대체하거나 사용자에게 보고한다
- 선행 PR이 아직 Merge/반영되지 않음
- `develop` 최신화 실패 (Fast-forward 불가, 충돌 등)
- Push 실패
- 동일 Head Branch의 PR이 이미 존재함 (새로 만들지 않고 기존 PR을 갱신)

임의의 Issue 번호나 없는 Label을 지어내지 않는다.
