# CLAUDE.md

Claude Code가 이 저장소에서 작업을 시작할 때 가장 먼저 참고하는 진입 문서다. 모든 AI Agent 공통 규칙은 이 문서가 아니라 [`AGENTS.md`](./AGENTS.md)에 있으며, 이 문서는 그 내용을 반복하지 않는다.

Claude Code는 `CLAUDE.md`만 자동으로 인식하고 `AGENTS.md`는 자동으로 읽지 않으므로, 세션 시작 시 `AGENTS.md`가 항상 함께 로드되도록 아래 Import를 사용한다.

@AGENTS.md

## 프로젝트 안내

- 프로젝트: GETI-Server, Spring Boot Backend 프로젝트
- 프로젝트 기본 구조, GitHub/AI 협업 기반, Configuration, Docker, Persistence, 공통 Web/API, CI 기반 구축을 마쳤다(PR 1~10, [`docs/audit/foundation-audit.md`](./docs/audit/foundation-audit.md)). 이후 최신 최소 19개 Table ERD를 기준으로 Domain Persistence 기반(JPA Entity, Repository, Flyway Migration)을 구성했다([`docs/architecture/erd.md`](./docs/architecture/erd.md)). Use Case(Service), Controller, OAuth Flow 등 실제 Domain 기능은 아직 구현되어 있지 않다.
- Issue와 명세를 기준으로 작업하고, 확인되지 않은 기능이나 Architecture를 추측해서 구현하지 않는다.
- 코드를 수정하기 전에 기존 코드와 테스트를 먼저 분석한다.

## 필수 문서 읽기 순서

```text
1. AGENTS.md (위 Import로 자동 로드됨)
2. docs/ai/README.md
3. 현재 Issue와 작업 명세
4. .claude/rules/ 관련 규칙
5. 관련 코드와 테스트
```

`CLAUDE.md`와 `.claude/rules/`의 내용은 `AGENTS.md`의 공통 규칙을 대체하지 않는다. 규칙이 서로 다르게 보이면 `AGENTS.md`의 우선순위 규칙(사용자 요청 > 현재 Issue > `AGENTS.md` > 도구별 지침 > `docs/ai` 세부 정책 > 기존 코드 패턴)을 따른다.

## 작업 시작 체크리스트

작업을 시작하기 전에 확인한다.

```text
- git status
- 현재 Branch
- 현재 Issue
- 완료 조건
- 제외 범위
- 관련 코드
- 관련 테스트
- 기존 구현 패턴
- 사용자 미커밋 변경
```

## 핵심 행동 규칙

- `AGENTS.md`를 모든 AI 공통 규칙의 기준으로 사용한다.
- 현재 Issue 범위를 벗어나지 않는다.
- 코드 수정 전에 관련 구현과 테스트를 탐색한다.
- 관련 없는 Refactoring을 수행하지 않는다.
- 기존 사용자의 변경 사항을 삭제하지 않는다.
- 확정되지 않은 Architecture를 임의로 도입하지 않는다.
- 테스트와 Build 없이 완료했다고 보고하지 않는다.
- Commit Type은 영문, 설명은 한글로 작성한다.
- 사용자가 요청한 경우에만 Commit, Push, PR을 수행한다.
- 사용자의 요청 없이 Merge하지 않는다.
- Force Push하지 않는다.
- Secret, Token, Password, Private Key를 출력하거나 Commit하지 않는다.
- 실행하지 않은 작업을 완료했다고 보고하지 않는다.
- API 또는 Controller를 추가·변경하면 같은 작업 안에서 [`docs/ai/openapi-documentation.md`](./docs/ai/openapi-documentation.md)의 Swagger/OpenAPI 문서화 규칙을 함께 적용하고 `OpenApiDocumentationTest`를 통과시킨다.

## 프로젝트 명령

Gradle Wrapper가 저장소에 포함되어 있으므로 Wrapper를 사용한다.

Windows:

```powershell
.\gradlew.bat test
.\gradlew.bat build
.\gradlew.bat spotlessApply
.\gradlew.bat check
.\gradlew.bat clean test build
```

Unix 또는 Git Bash:

```bash
./gradlew test
./gradlew build
./gradlew spotlessApply
./gradlew check
./gradlew clean test build
```

`check`(그리고 `clean test build`)는 `spotlessCheck`(포맷 검사)와 `detekt`(정적 분석)를 자동으로 포함한다. 포맷 위반이 있으면 `spotlessApply`로 먼저 정리한다. 도구별 설정은 [`docs/development/code-quality.md`](./docs/development/code-quality.md)를 따른다.

## Claude Code Rules

`.claude/rules/`의 Markdown 파일은 Claude Code가 세션 시작 시 자동으로 로드한다. 아래는 사람이 탐색할 때 참고할 목록이다.

- [`.claude/rules/repository-workflow.md`](./.claude/rules/repository-workflow.md) — 저장소 작업 실행 절차와 자율 판단 기준
- [`.claude/rules/spring-boot.md`](./.claude/rules/spring-boot.md) — Spring Boot/Kotlin 작업 원칙
- [`.claude/rules/testing.md`](./.claude/rules/testing.md) — 테스트 작성·실행·우회 금지 규칙
- [`.claude/rules/git-and-github.md`](./.claude/rules/git-and-github.md) — Branch, Commit, PR, Issue Label 규칙
- [`.claude/rules/security.md`](./.claude/rules/security.md) — Secret, Shell, Dependency 보안 규칙

## Commands와 Skills

`.claude/commands/`의 Markdown 파일은 Claude Code가 `/파일이름` 형태의 Slash Command로 자동 등록한다. `.claude/skills/*/SKILL.md`는 Claude Code가 자동으로 인식하는 공식 Skill 구조다.

- **Commands**: 사용자가 직접 호출하는 짧고 실행 중심의 Workflow. 세부 판단 기준을 복사하지 않고 관련 Skill을 참조한다.
- **Skills**: Command 실행 중 참고하는 상세 판단 기준, 예외 처리, 금지 사항.

Command를 실행하기 전에 관련 Skill을 먼저 참고한다. 어떤 Command도 사용자가 요청한 범위를 벗어나 Commit, Push, PR, Merge를 임의로 수행하지 않는다.

| Command | 목적 |
| --- | --- |
| [`/start-issue`](./.claude/commands/start-issue.md) | Issue 기반 작업 시작 (develop 최신화, Branch 생성, 상태 Label 전환) |
| [`/implement`](./.claude/commands/implement.md) | 현재 Issue의 기능/설정/문서 변경 구현 |
| [`/fix-bug`](./.claude/commands/fix-bug.md) | 버그 재현 및 원인 수정 |
| [`/review`](./.claude/commands/review.md) | 코드 리뷰 (기본적으로 수정 없음) |
| [`/verify`](./.claude/commands/verify.md) | Test/Build/링크/Secret 종합 검증 |
| [`/prepare-pr`](./.claude/commands/prepare-pr.md) | Commit, Push, Draft PR 준비 (명시적 요청 시에만) |

| Skill | 목적 |
| --- | --- |
| [`issue-workflow`](./.claude/skills/issue-workflow/SKILL.md) | Issue 분석, Branch, 상태 Label 흐름, 실패 처리 기준 |
| [`spring-boot-change`](./.claude/skills/spring-boot-change/SKILL.md) | Spring Boot/Kotlin 변경 시 환경 분석과 구현 원칙 |
| [`test-and-verify`](./.claude/skills/test-and-verify/SKILL.md) | 변경 유형별 테스트/검증 기준과 실패 분석 |
| [`code-review`](./.claude/skills/code-review/SKILL.md) | 기능/보안/성능/유지보수성 검토 기준 |
| [`pull-request`](./.claude/skills/pull-request/SKILL.md) | Commit/Push/Draft PR 준비 기준 |
| [`review-pr`](./.claude/skills/review-pr/SKILL.md) | GitHub PR 코드리뷰 요청 시 활성화(`/review-pr` 또는 자연어), GitHub에 인라인 Review 등록 |

권장 흐름: `/start-issue` → `/implement` 또는 `/fix-bug` → `/verify` → `/review` → `/prepare-pr`

`/review-pr`은 이미 GitHub에 올라간 PR을 사용자가 명시적으로 요청했을 때만 실행하는 별도 흐름이며, 위 순서에는 포함되지 않는다.

## 완료 보고

작업 완료 시 다음을 보고한다.

```text
- 분석 결과
- 변경 내용
- 변경 파일
- 실행한 Test와 Build
- 검증 결과
- Commit 및 Push 상태
- 남은 문제와 가정
```

자세한 완료 판단 기준은 [`docs/ai/completion-policy.md`](./docs/ai/completion-policy.md)를 따른다.
