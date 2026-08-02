# Codex 사용 안내

Codex CLI로 GETI-Server 저장소에서 작업할 때 참고하는 진입 문서다. 공통 AI 규칙은 이 문서가 아니라 [`AGENTS.md`](../AGENTS.md)에 있으며, 이 문서는 그 내용을 반복하지 않는다.

## 목적

- Codex CLI를 프로젝트에서 일관되게 사용한다.
- `AGENTS.md` 기반 공통 규칙을 준수한다.
- 반복되는 작업 유형(Issue 시작, 구현, 버그 수정, 리뷰, 검증, PR 준비)에 재사용할 수 있는 Prompt Template을 제공한다.
- Sandbox와 Approval 정책을 작업 유형에 맞게 안전하게 선택하도록 돕는다.
- Test 및 Build 검증 방식을 Claude Code와 동일하게 표준화한다.
- GitHub Issue와 PR Workflow의 일관성을 유지한다.

## AGENTS.md 자동 인식

Codex CLI는 저장소의 [`AGENTS.md`](../AGENTS.md)를 **별도 설정 없이 자동으로 읽어 지침(Instructions)으로 주입**한다. Claude Code가 `CLAUDE.md`만 자동 인식하고 `AGENTS.md`를 Import해야 하는 것과 달리, Codex는 `AGENTS.md`가 원래 지원하는 형식이다 (설치된 `codex.exe` 바이너리에 `AGENTS.md`를 읽어 `# AGENTS.md instructions` 형태로 지침에 포함시키는 로직과 개인 전용 `AGENTS.override.md` 지원이 확인됨).

따라서 Codex를 위한 별도의 `CLAUDE.md` 상당 진입 문서는 필요하지 않다. `AGENTS.md`가 곧 Codex의 진입 문서 역할을 한다.

## 문서 읽기 순서

```text
1. AGENTS.md (Codex가 자동으로 로드)
2. docs/ai/README.md
3. 현재 Issue와 작업 명세
4. .codex/policies/
5. 작업 유형에 맞는 .codex/prompts/
6. 관련 코드와 테스트
```

## 역할 구분

```text
AGENTS.md
→ Claude Code, Codex를 포함한 모든 AI Agent가 따라야 하는 공통 규칙 (Codex는 자동 로드)

docs/ai/
→ 공통 규칙의 상세 정책

.codex/README.md (이 문서)
→ Codex 사용 진입 문서

.codex/policies/
→ Codex 실행, Sandbox, Prompt 사용 정책

.codex/prompts/
→ 복사해서 사용하는 작업별 Prompt Template
```

## 실행 방식

현재 환경에서 확인된 버전은 `codex-cli 0.145.0`이다. 아래는 `codex --help`, `codex exec --help`로 직접 확인한 실행 방식이다.

대화형 실행:

```bash
codex
```

비대화형 실행 (`codex exec`, 별칭 `codex e`):

```bash
codex exec "<prompt>"
```

Prompt를 표준 입력으로 전달하는 것도 지원된다 (`codex exec --help`: "If not provided as an argument (or if `-` is used), instructions are read from stdin"):

```bash
codex exec - < .codex/prompts/verify-changes.md
```

`.codex/prompts/*.md`는 Codex가 자동으로 찾아서 등록하는 공식 기능이 아니다 (관련 자동 등록 문자열을 CLI 바이너리에서 찾지 못했다). 위처럼 파일 내용을 직접 Prompt로 전달하거나, 필요한 부분을 복사해서 사용하는 **템플릿 모음**으로 취급한다.

비대화형 코드 리뷰는 전용 명령이 있다 (`codex review --help`로 확인):

```bash
codex review --base develop
codex review --uncommitted
```

## Sandbox와 Approval

`codex --help`로 확인된 실제 옵션 값이다.

```text
--sandbox (-s)          read-only | workspace-write | danger-full-access
--ask-for-approval (-a) untrusted | on-request | never
```

작업 유형별 권장 값은 [`policies/sandbox-policy.md`](./policies/sandbox-policy.md)를 따른다. `--dangerously-bypass-approvals-and-sandbox`는 CLI 자체가 "EXTREMELY DANGEROUS"라고 명시하는 옵션이며 기본값으로 권장하지 않는다.

## Windows 환경

이 저장소는 Windows 11에서 `codex doctor`로 확인했다. 확인된 사실만 기록한다.

- `codex` CLI는 PowerShell과 Git Bash 양쪽에서 정상 실행된다 (이 세션에서 Bash 도구로 직접 확인).
- `codex sandbox` 하위 명령은 "Windows restricted token sandbox"로 명시되어 있어, Windows에서는 Linux/Mac과 다른 방식(제한된 Token 기반)으로 Sandbox를 구현한다.
- WSL 환경에서의 동작 차이는 이 세션에서 검증하지 못했다.

## 주의사항

- 사용자 전역 설정(`~/.codex/config.toml`, 인증 정보가 담긴 `~/.codex/auth.json` 등)은 이 저장소에 Commit하지 않는다.
- API Key나 인증 정보를 Prompt 또는 명령어에 직접 작성하지 않는다.
- `--dangerously-bypass-approvals-and-sandbox`와 같은 고위험 설정을 기본값으로 권장하지 않는다.
- Network Access가 필요하지 않은 작업(문서 검토, 정적 분석 등)에는 Network를 허용하지 않는다.
- Force Push와 사용자 요청 없는 Merge를 금지한다 ([`AGENTS.md`](../AGENTS.md) 공통 규칙).
- 실행하지 않은 Test를 성공으로 보고하지 않는다.

## Prompt Template

| Prompt | 목적 |
| --- | --- |
| [`start-issue.md`](./prompts/start-issue.md) | Issue 분석과 작업 Branch 준비 |
| [`implement-feature.md`](./prompts/implement-feature.md) | 기능/설정 변경 구현 |
| [`fix-bug.md`](./prompts/fix-bug.md) | 버그 재현과 원인 수정 |
| [`review-code.md`](./prompts/review-code.md) | 코드 리뷰 (기본적으로 수정 없음) |
| [`verify-changes.md`](./prompts/verify-changes.md) | Test/Build/링크/Secret 검증 |
| [`prepare-pr.md`](./prompts/prepare-pr.md) | Commit/Push/Draft PR 준비 (명시적 요청 시에만) |

권장 흐름: `start-issue` → `implement-feature` 또는 `fix-bug` → `verify-changes` → `review-code` → `prepare-pr`

## Skill

`.codex/prompts/`와 별개로 [`.agents/skills/review-pr/SKILL.md`](../.agents/skills/review-pr/SKILL.md)가 있다. `review-code` Prompt(로컬 Branch Diff, 아직 Push하지 않은 변경 대상)와 달리 이미 GitHub에 올라간 Pull Request를 대상으로 하며, 사용자가 PR 번호나 URL과 함께 코드리뷰를 명시적으로 요청했을 때(`#45 PR에 코드리뷰좀 해줘`, `$review-pr 45` 등) 활성화한다. GitHub Codex Code Review(`@codex review`)가 활성화된 환경에서도 `AGENTS.md`의 `## Code Review Rules`와 [`docs/ai/code-review.md`](../docs/ai/code-review.md)를 기준으로 검토한다. 상세 검토 기준, 심각도, Finding 조건은 모두 `docs/ai/code-review.md`에 있다.

## 정책 문서

| 정책 | 내용 |
| --- | --- |
| [`policies/execution-policy.md`](./policies/execution-policy.md) | 실행 전 확인 사항, 대화형/비대화형 실행 기준, 중단 조건, 금지 사항 |
| [`policies/sandbox-policy.md`](./policies/sandbox-policy.md) | 작업 유형별 최소 권한 Sandbox/Network 선택 기준 |
| [`policies/prompt-policy.md`](./policies/prompt-policy.md) | 좋은 Prompt의 구성 요소, Placeholder 표기, 금지되는 Prompt 방식 |
