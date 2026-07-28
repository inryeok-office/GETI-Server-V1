# AI 개발 문서

이 디렉터리는 Claude Code, Codex 등 AI 개발 도구가 GETI-Server 저장소에서 작업할 때 따라야 하는 세부 정책을 모아둔다.

## 목적

- Claude Code와 Codex 등 서로 다른 AI 도구가 동일한 핵심 규칙을 따르도록 한다.
- 도구마다 Git, 테스트, 보안 정책이 달라져 발생하는 혼선을 방지한다.
- 사람 협업자에게도 AI가 어떤 기준으로 작업하는지 투명하게 공개한다.

## `AGENTS.md`의 역할

저장소 Root의 [`AGENTS.md`](../../AGENTS.md)는 모든 AI Agent가 따라야 하는 **최상위 공통 지침**이다. 핵심 규칙과 이 디렉터리로의 링크만 담고 있으며, 세부 내용을 반복하지 않는다.

## `docs/ai/`의 역할

이 디렉터리의 문서들은 `AGENTS.md`가 요약한 규칙의 근거와 세부 기준을 담는다. 특정 상황에서 어떻게 판단해야 하는지 궁금하면 `AGENTS.md`가 아니라 이 디렉터리의 해당 문서를 확인한다.

## 공통 규칙과 도구별 규칙의 차이

- `AGENTS.md`, `docs/ai/*` : Claude Code, Codex를 포함한 **모든 AI 도구에 공통으로 적용**되는 규칙
- `CLAUDE.md`, `.claude/` : Claude Code에서만 사용하는 설정, Command, Skill (후속 단계에서 추가 예정)
- `.codex/` : Codex에서만 사용하는 실행 정책과 Prompt Template (후속 단계에서 추가 예정)

도구별 설정은 공통 규칙을 대체하지 않으며, 공통 규칙 위에서 도구에 특화된 사용 방법만 추가한다.

## 문서 읽기 순서

```text
1. AGENTS.md
2. docs/ai/README.md (이 문서)
3. docs/ai/workflow.md
4. 작업 유형에 맞는 세부 정책
5. 도구별 진입 문서 (CLAUDE.md 등, 추가되는 대로)
```

## 문서별 책임

| 문서 | 책임 |
| --- | --- |
| [`workflow.md`](./workflow.md) | 작업을 시작해서 끝낼 때까지의 표준 절차, Issue 기반 작업 방식 |
| [`coding-conventions.md`](./coding-conventions.md) | 코드 작성 시 지켜야 할 원칙과 변경 범위 기준 |
| [`git-conventions.md`](./git-conventions.md) | Branch, Commit, PR 등 AI 관점의 Git 규칙 |
| [`testing-policy.md`](./testing-policy.md) | 테스트 작성·유지·실행 기준과 검증 정책 |
| [`security-policy.md`](./security-policy.md) | Secret 관리, 위험한 명령 제한 등 보안 정책 |
| [`completion-policy.md`](./completion-policy.md) | 작업을 "완료"로 판단하는 기준과 보고 형식 |

## 규칙 우선순위

`AGENTS.md`에 정의된 우선순위를 그대로 따른다.

```text
1. 사용자의 현재 명시적 요청
2. 현재 Issue와 작업 명세
3. AGENTS.md
4. 도구별 지침
5. docs/ai 세부 정책
6. 기존 코드의 일관된 패턴
```

## 이후 추가될 설정 안내

이번 단계에서는 공통 AI 규칙(`AGENTS.md`, `docs/ai/`)만 구성한다. 같은 Issue의 후속 단계에서 다음을 추가할 예정이다.

- `CLAUDE.md` : Claude Code 진입 문서
- `.claude/rules/`, `.claude/commands/`, `.claude/skills/` : Claude Code 전용 규칙, Command, Skill
- `.codex/` : Codex 실행 정책과 작업 유형별 Prompt Template

아직 위 파일들은 존재하지 않으므로, 이 문서를 포함한 어떤 문서에서도 위 경로를 유효한 링크로 연결하지 않는다.
