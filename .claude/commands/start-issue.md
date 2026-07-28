---
description: GitHub Issue 기반 작업을 안전하게 시작한다 (develop 최신화, Branch 생성, 상태 Label 전환)
argument-hint: <issue-number>
---

## 목적

GitHub Issue 번호를 받아 작업을 시작할 수 있는 상태(최신 `develop` 기준 작업 Branch, `in progress` 상태)까지 준비한다.

## 필요 입력

Issue 번호: `$1`

Issue 번호가 제공되지 않았다면 임의로 선택하거나 추측하지 않고 사용자에게 확인한다.

## 참조

시작하기 전에 [`AGENTS.md`](../../AGENTS.md), [`CLAUDE.md`](../../CLAUDE.md)를 확인한다. 상세 판단 기준은 [`issue-workflow` Skill](../skills/issue-workflow/SKILL.md)을 따른다.

## 수행 절차

1. `git status`와 현재 Branch 확인 — 미커밋 변경이 있으면 출처를 분석하고 사용자 변경을 보호한다.
2. `gh issue view $1`로 Issue 제목, 본문, 완료 조건, 제외 범위, Label을 확인한다.
3. 선행 PR/Dependency가 있다면 실제로 Merge되었는지 확인한다.
4. `git switch develop && git pull --ff-only origin develop`로 `develop`을 최신화한다.
5. Issue 번호 기반 작업 Branch가 이미 있는지 확인한다.
6. 없으면 `git switch -c <type>/$1-{short-description} develop`로 생성한다. Branch 형식은 저장소 Git Convention(`README.md`, `docs/ai/git-conventions.md`)을 따른다.
7. Issue 상태 Label을 저장소에 실제 존재하는 이름으로 `ready` → `in progress`로 변경한다.
8. 구현 전 간단한 작업 계획을 작성한다.

이 Command는 여기까지만 수행한다. 코드 구현, Commit, Push, PR 생성은 하지 않는다.

## 중단 조건

다음이면 진행을 멈추고 원인을 보고한다.

- Working Tree에 출처 불명의 변경이 있음
- 선행 PR이 아직 반영되지 않음
- Issue를 찾을 수 없음
- 현재 Branch가 이미 다른 작업 중인 Branch임
- `develop` 최신화 실패 (Fast-forward 불가 등)
- GitHub 인증 실패

사용자의 변경을 자동으로 Stash, Reset, 삭제하지 않는다.

## 결과 보고

- Issue 번호와 제목
- 완료 조건, 제외 범위
- Base Branch, 작업 Branch
- Label 변경 내역
- 구현 계획
- 중단되었다면 원인과 위험 요소
