# AGENTS.md

이 문서는 Claude Code, Codex 등 이 저장소에서 작업하는 모든 AI Agent가 따라야 하는 최상위 공통 지침이다.

세부 정책은 [`docs/ai/`](./docs/ai/README.md)에 분리되어 있다. 이 문서는 핵심 규칙과 각 세부 문서로의 링크만 제공한다.

## 프로젝트 개요

- 프로젝트 이름: GETI-Server (Spring Boot Backend)
- Kotlin(Gradle Kotlin DSL) 기반이며 Repository/AI Harness/Code Quality/Test/Spring Modulith/Configuration/Docker/Persistence/공통 Web·API/CI 기반을 모두 갖춘 **기반 구축 완료 단계**다(PR 1~10, [`docs/audit/foundation-audit.md`](./docs/audit/foundation-audit.md) 참고). 이후 최신 최소 19개 Table ERD를 기준으로 한 **Domain Persistence 기반**(JPA Entity, Repository, Flyway Migration)을 구성했다([`docs/architecture/erd.md`](./docs/architecture/erd.md) 참고). Use Case(Service), Controller, OAuth Flow 등 실제 Domain 기능(Auth, Member, Job 등)은 아직 구현되어 있지 않다.
- 확인되지 않은 기능이나 Architecture를 추측해서 구현하지 않는다. 항상 Issue와 명세, 그리고 실제 Domain 기능이면 GETI Notion(기능명세서, API 명세서, PRD)을 기준으로 작업하고, 작업 전 기존 코드와 테스트를 먼저 분석한다. Notion과 저장소가 다르면 [`docs/audit/notion-repository-sync.md`](./docs/audit/notion-repository-sync.md)의 분류 기준을 따른다.

## 규칙 우선순위

충돌 시 다음 순서를 따른다.

```text
1. 사용자의 현재 명시적 요청
2. 현재 Issue와 작업 명세
3. GETI Notion의 명확히 확정된 제품 요구사항/API 계약(PRD, 기능명세서, API 명세서)
4. Repository의 실제 Build/Test/Code(이미 구현되고 검증된 동작)
5. AGENTS.md
6. docs/architecture (Architecture 단일 기준)
7. 도구별 지침 (CLAUDE.md, .claude/rules, .codex/ 등)
8. docs/ai, docs/development 세부 정책
9. Notion의 초안이거나 저장소 실제 구현과 명백히 오래된 페이지
10. 기존 코드의 일관된 패턴, 개인 취향
```

3번(Notion 확정 요구사항)과 4번 또는 9번(저장소 실제 구현)이 서로 다른 내용을 말하면, 어느 쪽이 "확정"이고 어느 쪽이 "오래됨"인지 AI Agent가 임의로 판단하지 않는다. 대신 `CONTRACT_MISMATCH` 또는 `DECISION_REQUIRED`로 분류해 보고하고 사용자 결정을 기다린다. 실제 판단 사례와 현재 알려진 불일치 목록은 [`docs/audit/notion-repository-sync.md`](./docs/audit/notion-repository-sync.md)를 따른다.

상위 요청이 보안 또는 저장소 안전 원칙을 위반할 가능성이 있다면 작업을 그대로 진행하지 않고 위험을 명확하게 보고한다.

## 필수 작업 순서

```text
1. Git 상태 확인
2. 관련 문서 확인
3. 기존 코드와 테스트 탐색
4. Issue 범위와 완료 조건 확인
5. 영향 범위 분석
6. 변경 계획 수립
7. 최소 범위 구현
8. 관련 테스트 실행
9. 전체 검증
10. Diff 자체 리뷰
11. 요청된 경우 Commit 및 Push
12. 결과 보고
```

코드를 먼저 수정한 뒤 저장소 구조를 파악하는 방식은 금지한다. 자세한 단계별 기준은 [`docs/ai/workflow.md`](./docs/ai/workflow.md)를 따른다.

## 작업 범위

- Issue에 없는 기능을 임의로 추가하지 않는다.
- 관련 없는 Refactoring을 함께 수행하지 않는다.
- 기존 구현을 확인하지 않고 중복 구현하지 않는다.
- 빈 Class와 빈 Package를 과도하게 생성하지 않는다.
- 확정되지 않은 Architecture를 사실처럼 구현하지 않는다.
- 사용자의 기존 변경 사항을 삭제하거나 되돌리지 않는다.
- 중요 가정은 완료 보고에 명시한다.
- 작업 범위가 크면 논리적인 단계나 Commit으로 나눈다.
- TODO나 Placeholder로 핵심 요구사항을 남기고 완료 처리하지 않는다.

## Git 규칙

- `main`과 `develop`에서 직접 작업하지 않는다.
- Issue 번호 기반 Branch를 사용한다.
- 현재 작업과 관련된 파일만 Stage한다.
- Commit 전 `git status`, `git diff`, `git diff --staged`를 확인한다.
- 한 Commit에는 하나의 논리적인 변경을 담는다.
- Conventional Commit Type은 영문으로 유지하고, Commit 설명은 반드시 한글로 작성한다.
- 사용자의 명시적 요청 없이 Push하지 않는다.
- 사용자의 요청 없이 Merge하지 않는다.
- Force Push하지 않는다.
- 공유 History를 임의로 Rewrite하지 않는다.

Commit 형식:

```text
<type>: <한글 작업 내용>
```

예시:

```text
chore: AI 공통 작업 규칙 추가
docs: 테스트 및 완료 기준 문서화
feat: 채용 공고 북마크 기능 추가
fix: 로그인 토큰 재발급 오류 수정
refactor: 사용자 권한 검증 로직 분리
```

허용 Type:

```text
feat
fix
refactor
chore
docs
test
config
build
ci
perf
style
revert
```

Branch 전략, Label 흐름, PR 규칙 등 Git Flow 전반은 [`docs/ai/git-conventions.md`](./docs/ai/git-conventions.md)와 저장소 [`README.md`](./README.md)를 따른다.

## 파괴적 명령 제한

다음 명령은 사용자의 명시적 요청과 영향 범위 확인 없이 실행하지 않는다.

```bash
git reset --hard
git clean -fd
git checkout -- .
git restore .
git push --force
git push --force-with-lease
rm -rf
docker compose down -v
```

`docker compose down -v`는 로컬 PostgreSQL/Redis/MinIO Named Volume 데이터를 삭제한다([`docs/development/docker.md`](./docs/development/docker.md) 참고).

## 테스트 및 검증

- 테스트하지 않고 완료했다고 보고하지 않는다.
- 기존 테스트를 삭제하거나 비활성화하여 통과시키지 않는다.
- 실패한 테스트의 원인을 분석하고, 환경 문제와 코드 문제를 구분한다.
- 실행하지 못한 검증은 명확히 보고한다.
- 변경 범위에 맞는 테스트를 먼저 실행하고 마지막에 전체 Build를 수행한다.
- 경고를 오류처럼 과장하지 않고, 오류를 경고로 축소하지 않는다.

Kotlin 코드를 변경했다면 Build 전에 포맷/정적 분석을 실행한다. `spotlessCheck`와 `detekt`는 `check`에 이미 포함되어 있어 `clean test build` 한 번으로도 함께 실행된다.

```bash
./gradlew spotlessApply   # 포맷이 흐트러졌다면 자동 적용
./gradlew clean test build
```

Windows에서는 `.\gradlew.bat`를 사용한다.

세부 기준은 [`docs/ai/testing-policy.md`](./docs/ai/testing-policy.md)와 [`docs/development/code-quality.md`](./docs/development/code-quality.md)를 따른다.

## 보안

- Secret, Token, Password, 인증서, Private Key를 코드에 작성하지 않는다.
- 실제 Secret 값을 예시로 사용하지 않는다.
- `.env`, 인증서, Key 파일을 Commit하지 않는다.
- Secret 파일 내용을 출력하지 않는다.
- 로그에 민감정보를 출력하지 않는다.
- 인증과 인가를 테스트 편의를 위해 제거하지 않는다.
- 외부 Script를 검증 없이 실행하지 않는다.
- 운영 데이터에 직접 접근하거나 수정하지 않는다.
- 실제 사용자 정보를 Test Data로 사용하지 않는다.

세부 내용은 [`docs/ai/security-policy.md`](./docs/ai/security-policy.md)를 따른다.

## 완료 보고

최종 보고에는 다음을 포함한다.

```text
1. 분석 결과
2. 구현 내용
3. 변경 파일
4. 주요 판단과 가정
5. 실행한 검증
6. 검증 결과
7. 실행하지 못한 검증
8. Commit 상태
9. Push 및 PR 상태
10. 남은 작업과 위험 요소
```

실제로 생성하거나 수행하지 않은 Issue, Commit, Push, PR, Test를 완료했다고 보고하지 않는다. 완료 여부 판단 기준은 [`docs/ai/completion-policy.md`](./docs/ai/completion-policy.md)를 따른다.

## 코딩 컨벤션

기존 코드 스타일 유지, 불필요한 추상화 금지 등 코드 작성 원칙은 [`docs/ai/coding-conventions.md`](./docs/ai/coding-conventions.md)를 따른다.

API 또는 Controller를 추가·변경하는 작업은 Swagger/OpenAPI 문서화와 자동 검증을 같은 PR에서 수행해야 하며, [`docs/ai/openapi-documentation.md`](./docs/ai/openapi-documentation.md) 규칙을 반드시 따른다.

## 세부 문서

```text
docs/ai/README.md              AI 개발 문서 진입점 및 읽기 순서
docs/ai/workflow.md             표준 작업 Workflow
docs/ai/coding-conventions.md   코딩 및 변경 범위 원칙
docs/ai/git-conventions.md      Git 및 한글 Commit 규칙
docs/ai/testing-policy.md       테스트 및 검증 정책
docs/ai/security-policy.md      보안 및 위험 작업 방지 정책
docs/ai/completion-policy.md    완료 판단 및 결과 보고 정책
docs/ai/openapi-documentation.md        Swagger/OpenAPI 문서화 필수 규칙과 자동 검증
docs/development/quick-start.md         신규 개발자용 로컬 환경 구성 순서
docs/audit/foundation-audit.md          PR 1~10 전체 기반 Audit 결과
docs/audit/notion-repository-sync.md    GETI Notion과 저장소 불일치 목록, DECISION_REQUIRED
docs/audit/ai-scenario-audit.md         AI 개발 시나리오 Static Audit 결과
docs/automation/claude-daily-audit-routine.md   Claude 일일 자동 점검 Routine 실행 기준과 최종 프롬프트
```

Claude Code 전용 설정은 [`CLAUDE.md`](./CLAUDE.md)와 `.claude/`(`rules/`, `commands/`, `skills/`)에, Codex 전용 설정은 `.codex/`(`policies/`, `prompts/`)에 있다.
