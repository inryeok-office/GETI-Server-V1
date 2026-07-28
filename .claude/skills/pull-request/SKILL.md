---
name: pull-request
description: 검증된 변경만 안전하게 Commit, Push, Draft Pull Request로 연결하는 절차와 기준을 다룬다.
---

# Pull Request

GETI-Server에서 Commit, Push, PR을 준비할 때 참고하는 상세 기준이다. [`prepare-pr` Command](../../commands/prepare-pr.md)가 이 Skill을 참조한다.

## PR 전 확인

- 현재 Issue의 완료 조건과 제외 범위
- 현재 Branch가 올바른 작업 Branch인지
- Base Branch(`develop`)가 최신인지
- 동일 Head Branch의 기존 PR 존재 여부 (`gh pr list --head <branch>`)
- Working Tree와 Diff
- Test와 Build 결과
- Secret과 불필요한 파일 포함 여부

## Commit

- 관련된 파일만 Stage한다.
- 하나의 Commit에는 하나의 논리적 단위만 담는다.
- Type은 영문 소문자, 설명은 한글로 작성한다: `<type>: <한글 작업 내용>`.
- Commit이 실패하면(Hook 실패 등) 원인을 파악하고, 실패를 성공으로 보고하지 않는다.

## Push

- 현재 Branch와 Upstream을 확인한다.
- 일반 `git push`만 사용한다.
- Force Push(`--force`, `--force-with-lease`)를 하지 않는다.
- Push가 실패하면 정확한 오류를 보고한다.

## Draft PR

- Base Branch는 기본적으로 `develop`이다.
- 동일 Head Branch의 PR이 이미 있으면 새로 만들지 않고 기존 PR 본문을 갱신한다.
- PR 본문에 다음을 포함한다.
  - 관련 Issue 연결 (`Closes #{issue-number}`)
  - 변경 내용
  - 주요 설계 판단
  - 실제 실행한 검증과 결과
  - 영향 범위
  - 제외 범위
  - 실제로 검증한 항목만 체크된 체크리스트

## Label

- `gh label list`로 저장소에 실제 존재하는 Label만 확인해서 적용한다.
- 존재하지 않는 Label을 임의로 새로 만들지 않는다.
- 작업 유형(`🧹 chore` 등)과 영향 영역(`area:` 등) Label은 PR에도 적용한다.
- 상태 Label(`in progress`, `review` 등)은 Issue에만 적용하고 PR에는 적용하지 않는다 ([`docs/ai/git-conventions.md`](../../../docs/ai/git-conventions.md) 참고).

## Issue 상태

PR을 생성하면 Issue 상태 Label을 다음과 같이 전환한다.

```text
in progress → review
```

PR이 Merge된 뒤 Issue를 Close하고 `review` 이후 상태를 정리하는 것은 이 Skill의 범위가 아니라 별도 작업(사용자 요청 또는 Merge 시점)으로 남긴다.

## 금지 사항

- 사용자의 명시적 요청 없이 Commit, Push, PR 생성
- Force Push
- 중복 PR 생성
- 사용자 요청 없는 Merge
- 검증하지 않은 항목을 체크리스트에서 체크 표시
