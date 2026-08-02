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
- `CLAUDE.md`, `.claude/rules/` : Claude Code에서만 사용하는 진입 문서와 작업 규칙 (구성 완료)
- `.claude/commands/`, `.claude/skills/` : Claude Code 전용 반복 작업 Command/Skill (구성 완료)
- `.codex/` : Codex에서만 사용하는 실행 정책과 Prompt Template (구성 완료)

도구별 설정은 공통 규칙을 대체하지 않으며, 공통 규칙 위에서 도구에 특화된 사용 방법만 추가한다.

## 문서 읽기 순서

```text
1. AGENTS.md
2. docs/ai/README.md (이 문서)
3. docs/ai/workflow.md
4. 작업 유형에 맞는 세부 정책
5. 도구별 진입 문서 (Claude Code는 `CLAUDE.md`, Codex는 자동 인식되는 `AGENTS.md` 자체)
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
| [`openapi-documentation.md`](./openapi-documentation.md) | Swagger/OpenAPI 문서화 필수 규칙과 자동 검증(`OpenApiDocumentationTest`) |
| [`code-review.md`](./code-review.md) | Claude Code/Codex 공통 GitHub PR 코드리뷰 정책, 심각도, Finding 조건 |

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

## Claude Code 전용 설정

Claude Code는 저장소 Root의 [`CLAUDE.md`](../../CLAUDE.md)를 세션 시작 시 자동으로 발견해서 로드한다. `CLAUDE.md`는 `AGENTS.md`를 Import(`@AGENTS.md`)해 공통 규칙이 항상 함께 로드되도록 하고, 그 위에 Claude Code 전용 안내(문서 읽기 순서, 작업 시작 체크리스트, 핵심 행동 규칙, 프로젝트 명령)를 추가한다.

`.claude/rules/`의 Markdown 파일도 Claude Code가 세션 시작 시 자동으로 로드한다.

- [`.claude/rules/repository-workflow.md`](../../.claude/rules/repository-workflow.md) — 저장소 작업 실행 절차, 자율 판단 기준
- [`.claude/rules/spring-boot.md`](../../.claude/rules/spring-boot.md) — Spring Boot/Kotlin 작업 원칙
- [`.claude/rules/testing.md`](../../.claude/rules/testing.md) — 테스트 작성·실행·우회 금지 규칙
- [`.claude/rules/git-and-github.md`](../../.claude/rules/git-and-github.md) — Branch, Commit, PR, Issue Label 규칙
- [`.claude/rules/security.md`](../../.claude/rules/security.md) — Secret, Shell, Dependency 보안 규칙

`CLAUDE.md` 자동 로드와 `.claude/rules/` 자동 로드는 Claude Code 공식 기능이다. 이 저장소에서는 로컬 CLI 캐시(`~/.claude/cache/changelog.md`)의 공식 Changelog로 확인했다.

### Commands와 Skills

`.claude/commands/`의 각 Markdown 파일은 Claude Code가 `/파일이름` 형태의 Slash Command로 자동 등록한다. `.claude/skills/*/SKILL.md`는 Claude Code가 자동으로 인식하는 공식 Skill 구조다. 두 기능 모두 로컬 CLI 캐시의 공식 Changelog로 확인했다.

- Command : 사용자가 직접 호출하는 짧고 실행 중심의 Workflow
- Skill : Command가 참조하는 상세 판단 기준, 예외 처리, 금지 사항

| Command | 목적 |
| --- | --- |
| [`start-issue`](../../.claude/commands/start-issue.md) | Issue 기반 작업 시작 |
| [`implement`](../../.claude/commands/implement.md) | 기능/설정/문서 변경 구현 |
| [`fix-bug`](../../.claude/commands/fix-bug.md) | 버그 재현 및 원인 수정 |
| [`review`](../../.claude/commands/review.md) | 코드 리뷰 |
| [`verify`](../../.claude/commands/verify.md) | Test/Build/링크/Secret 검증 |
| [`prepare-pr`](../../.claude/commands/prepare-pr.md) | Commit/Push/Draft PR 준비 |

| Skill | 목적 |
| --- | --- |
| [`issue-workflow`](../../.claude/skills/issue-workflow/SKILL.md) | Issue 분석, Branch, 상태 Label 흐름 |
| [`spring-boot-change`](../../.claude/skills/spring-boot-change/SKILL.md) | Spring Boot/Kotlin 변경 원칙 |
| [`test-and-verify`](../../.claude/skills/test-and-verify/SKILL.md) | 변경 유형별 테스트/검증 기준 |
| [`code-review`](../../.claude/skills/code-review/SKILL.md) | 코드 리뷰 검토 기준 |
| [`pull-request`](../../.claude/skills/pull-request/SKILL.md) | Commit/Push/Draft PR 준비 기준 |
| [`review-pr`](../../.claude/skills/review-pr/SKILL.md) | GitHub PR 코드리뷰 요청 시 `/review-pr` 또는 자연어로 활성화, GitHub에 인라인 Review 등록 |

권장 흐름: `start-issue` → `implement` 또는 `fix-bug` → `verify` → `review` → `prepare-pr`

`review-pr`은 위 순서에 포함되지 않는 별도 흐름이다. 로컬 작업 중의 `review`(코드 수정 전 Diff 검토)와 달리, 이미 GitHub에 올라간 PR을 대상으로 사용자가 명시적으로 요청했을 때만 실행한다.

### 현재까지 지원 확인 범위

- 자동 인식이 확인된 기능: `CLAUDE.md` 자동 로드, `AGENTS.md` 비자동 인식, `.claude/rules/*.md` 자동 로드, `.claude/settings.json`/`.claude/settings.local.json` 구분, `.claude/commands/*.md` Slash Command 등록, `.claude/skills/*/SKILL.md` Skill 인식 — 모두 로컬 CLI 캐시의 공식 Changelog로 확인
- 자동 인식을 확인하지 못한 부분: `@path/to/file.md` Import 문법의 실제 런타임 동작(외부 경로 승인 다이얼로그 등)은 세션 재시작으로 직접 관찰하지 못함
- Command/Skill 파일은 문법 오류가 있어도 일반 Markdown 문서로서 사람이 읽고 참고하는 데는 문제가 없도록 작성했다

## Codex 전용 설정

Codex CLI는 저장소의 [`AGENTS.md`](../../AGENTS.md)를 **별도 설정 없이 자동으로 읽어 지침으로 주입**한다 (설치된 `codex.exe` 바이너리에서 `AGENTS.md`를 읽어 지침에 포함시키는 로직과 개인 전용 `AGENTS.override.md` 지원을 직접 확인했다). Claude Code처럼 `CLAUDE.md`에 해당하는 별도 진입 문서가 필요하지 않고, `AGENTS.md` 자체가 Codex의 진입 문서 역할을 한다.

- [`.codex/README.md`](../../.codex/README.md) — Codex 사용 진입 문서, 실행 방식, Sandbox/Approval 값, Windows 환경 확인 사항
- [`.codex/policies/execution-policy.md`](../../.codex/policies/execution-policy.md) — 실행 전 확인, 대화형/비대화형 실행 기준, 중단 조건
- [`.codex/policies/sandbox-policy.md`](../../.codex/policies/sandbox-policy.md) — 작업 유형별 최소 권한 Sandbox/Network 선택 기준
- [`.codex/policies/prompt-policy.md`](../../.codex/policies/prompt-policy.md) — Prompt 구성 요소, Placeholder 표기, 금지되는 Prompt 방식

`.codex/prompts/`의 Prompt Template 6종(`start-issue`, `implement-feature`, `fix-bug`, `review-code`, `verify-changes`, `prepare-pr`)은 Claude Code의 `.claude/commands/`와 달리 **Codex가 자동으로 찾아서 등록하는 공식 기능이 아니다** (설치된 CLI 바이너리에서 관련 자동 등록 로직을 찾지 못했다). 내용을 복사하거나 `codex exec - < 파일` 형태로 표준 입력에 전달해서 사용하는 템플릿 모음으로 취급한다.

[`.agents/skills/review-pr/SKILL.md`](../../.agents/skills/review-pr/SKILL.md)는 `.codex/prompts/`와 별개로 도입한 GitHub PR 코드리뷰 전용 Skill이다. `.codex/prompts/review-code.md`(로컬 Branch Diff 리뷰, 아직 Push하지 않은 변경 대상)와 달리 이미 GitHub에 올라간 PR을 대상으로 하며, 자연어 요청(`#45 PR에 코드리뷰좀 해줘` 등)이나 `$review-pr {번호 또는 URL}` 명시적 호출로 활성화한다. 상세 정책은 [`docs/ai/code-review.md`](./code-review.md)를 따른다.

### 사용자 전역 설정을 저장소에 포함하지 않는 이유

`~/.codex/config.toml`(Model, Sandbox, Approval 기본값 등)과 `~/.codex/auth.json`(인증 토큰)은 사용자별 환경과 인증 정보를 담는 개인 전역 파일이다. 이 값을 저장소에 Commit하면 다른 사용자의 환경을 덮어쓰거나 인증 정보가 유출될 위험이 있어, 저장소에는 문서(사용법·정책·Template)만 포함하고 실제 설정 파일은 포함하지 않는다.

### 현재까지 확인한 Codex 지원 범위

- 설치 버전: `codex-cli 0.145.0` (`codex --version`)
- 확인된 기능: `codex`(대화형), `codex exec`(비대화형, 인자 또는 표준 입력으로 Prompt 전달), `codex review --base <branch>`/`--uncommitted`(전용 코드 리뷰 명령), `-s/--sandbox`(`read-only`/`workspace-write`/`danger-full-access`), `-a/--ask-for-approval`(`untrusted`/`on-request`/`never`), `AGENTS.md` 자동 로드와 `AGENTS.override.md` — 모두 `codex --help`, `codex exec --help`, `codex review --help`, `codex doctor` 실행 결과와 설치된 CLI 바이너리의 문자열 검사로 확인
- 확인하지 못한 부분: `.codex/prompts/`의 실제 자동 등록 여부(위 근거로 "아니다"에 무게를 두었으나 CLI 소스 전체를 확인한 것은 아님), WSL 환경에서의 동작 차이

## 구성 완료 상태

이번 PR(AI 기반 개발 환경 및 하네스 구성)의 범위는 공통 규칙, Claude Code 하네스, Codex 하네스까지다. 세 영역 모두 구성과 최종 검토를 마쳤다.

새로운 AI 도구 추가나 Command/Skill/Prompt 확장은 이 문서가 갱신될 별도의 후속 Issue에서 다룬다.
