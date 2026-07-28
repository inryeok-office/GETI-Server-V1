# CLAUDE.md

Claude Code가 이 저장소에서 작업을 시작할 때 가장 먼저 참고하는 진입 문서다. 모든 AI Agent 공통 규칙은 이 문서가 아니라 [`AGENTS.md`](./AGENTS.md)에 있으며, 이 문서는 그 내용을 반복하지 않는다.

Claude Code는 `CLAUDE.md`만 자동으로 인식하고 `AGENTS.md`는 자동으로 읽지 않으므로, 세션 시작 시 `AGENTS.md`가 항상 함께 로드되도록 아래 Import를 사용한다.

@AGENTS.md

## 프로젝트 안내

- 프로젝트: GETI-Server, Spring Boot Backend 프로젝트
- 현재 프로젝트 기본 구조와 GitHub/AI 협업 기반을 갖추는 **초기 구축 단계**다. 아직 실제 도메인 기능은 구현되어 있지 않다.
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

## 프로젝트 명령

Gradle Wrapper가 저장소에 포함되어 있으므로 Wrapper를 사용한다.

Windows:

```powershell
.\gradlew.bat test
.\gradlew.bat build
.\gradlew.bat clean test build
```

Unix 또는 Git Bash:

```bash
./gradlew test
./gradlew build
./gradlew clean test build
```

## Claude Code Rules

`.claude/rules/`의 Markdown 파일은 Claude Code가 세션 시작 시 자동으로 로드한다. 아래는 사람이 탐색할 때 참고할 목록이다.

- [`.claude/rules/repository-workflow.md`](./.claude/rules/repository-workflow.md) — 저장소 작업 실행 절차와 자율 판단 기준
- [`.claude/rules/spring-boot.md`](./.claude/rules/spring-boot.md) — Spring Boot/Kotlin 작업 원칙
- [`.claude/rules/testing.md`](./.claude/rules/testing.md) — 테스트 작성·실행·우회 금지 규칙
- [`.claude/rules/git-and-github.md`](./.claude/rules/git-and-github.md) — Branch, Commit, PR, Issue Label 규칙
- [`.claude/rules/security.md`](./.claude/rules/security.md) — Secret, Shell, Dependency 보안 규칙

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
