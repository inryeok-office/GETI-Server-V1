# 전체 기반 Audit (PR 1~10)

이 문서는 실제 Domain 기능 개발을 시작하기 전, PR 1부터 PR 10까지 구성한 기반이 서로 충돌 없이 동작하는지, 문서와 실제 코드가 일치하는지 실측한 결과다. 대화에서 계획했던 내용이 아니라 실제 저장소 파일과 Git History, 실제 실행한 명령 결과만 기록한다. 실제 Domain 기능은 포함하지 않는다.

검증 시점: 2026-07-29. 기준 Commit: `develop` `63b2255`(PR #22 Merge 직후).

## 전체 기반 인벤토리

| # | 기반 | 관련 PR/Issue | 핵심 파일 | 상태 |
| --- | --- | --- | --- | --- |
| 1 | Repository Foundation | PR #2(Issue #1) | `settings.gradle.kts`, `build.gradle.kts`, `.gitignore`, `.github/ISSUE_TEMPLATE/*`, `.github/pull_request_template.md`, Label 체계 | 완료 |
| 2 | AI Development Harness | PR #4(Issue #3) | `AGENTS.md`, `CLAUDE.md`, `docs/ai/*`, `.claude/rules`, `.claude/commands`, `.claude/skills`, `.codex/*` | 완료 |
| 3 | Kotlin Code Quality | PR #6(Issue #5) | Spotless(ktlint 1.8.0), detekt(2.0.0-alpha.3), `.editorconfig`, `config/detekt/detekt.yml` | 완료 |
| 4 | Kotlin Test Harness | PR #8(Issue #7) | JUnit 5, Kover(0.9.9), `GetiServerApplicationTests` | 완료 |
| 5 | Spring Modulith Foundation | PR #10(Issue #9) | `spring-modulith-starter-test`(1.4.1), `ModularityTest`, `ModuleDocumentationTest` | 완료 |
| 6 | Configuration Foundation | PR #12(Issue #11) | `application.yaml`/`-local.yaml`, `ApplicationProfileConfigurationTest` | 완료 |
| 7 | Docker & Local Infrastructure | PR #14(Issue #13) | `compose.yaml`(PostgreSQL/Redis/MinIO), `Dockerfile`, `.env.example` | 완료 |
| - | Package Structure Refactoring | PR #16(Issue #15) | Root Package `team.inreok.geti.getiserver` → `team.inreok.getiserver` | 완료 |
| 8 | Persistence & Data Access | PR #18(Issue #17) | Spring Data JPA, Flyway, Redis, Testcontainers, `src/integrationTest` | 완료 |
| 9 | Common Web & API Foundation | PR #20(Issue #19) | `team.inreok.getiserver.web`(`ApiResponse`, `ErrorResponse`, `GlobalExceptionHandler` 등), Actuator | 완료 |
| 10 | CI & Repository Policy | PR #22(Issue #21) | `.github/workflows/ci.yml`, Discord CI 알림, Dependabot, PR Template | 완료 |

Git History(`git log --oneline --decorate`)와 GitHub PR 상태(`gh pr list --state all`)로 위 10개 PR + 1개 중간 PR이 모두 `develop`에 Merge된 것을 확인했다. 대화 계획과 달리 실제로 구현되지 않은 항목은 없었다(`NOT_IMPLEMENTED` 없음).

## 현재 저장소 실측 정보

| 항목 | 값 | 근거 |
| --- | --- | --- |
| Kotlin | 2.3.21 | `build.gradle.kts` |
| Spring Boot | 4.1.0 | `build.gradle.kts` |
| Spring Framework | 7.0.8 | 실행 Log(`Running with Spring Boot v4.1.0, Spring v7.0.8`) |
| Java Toolchain | 25 | `build.gradle.kts`, `Dockerfile`(`eclipse-temurin:25.0.3_9`) |
| Gradle | 9.5.1 | `gradle/wrapper/gradle-wrapper.properties` |
| Jackson | 3.1.4(`tools.jackson`) | `./gradlew dependencies` 실측 |
| Spring Modulith | 1.4.1 | `build.gradle.kts` |
| detekt | 2.0.0-alpha.3 | `build.gradle.kts` |
| Spotless/ktlint | 8.9.0 / 1.8.0 | `build.gradle.kts` |
| Kover | 0.9.9 | `build.gradle.kts` |
| Testcontainers | 2.0.5(BOM 직접 Import) | `build.gradle.kts` |
| Hibernate Validator | 9.1.0.Final | `./gradlew dependencies` 실측 |
| Root Package | `team.inreok.getiserver` | 실제 Source Tree |
| Application Module 수(Spring Modulith) | 1개(`web`) | `ApplicationModules.of(...)` 실측(PR 9 시점) |
| Production Kotlin 파일 | 8개(`GetiServerApplication` 1 + `web` 7) | `find src/main/kotlin` |
| Version Catalog | 없음(`gradle/libs.versions.toml` 미존재) | 파일 시스템 확인 |
| 단일/멀티 모듈 | 단일 모듈(`rootProject.name = "GETI-Server"`) | `settings.gradle.kts` |

## Architecture 검증

- `team.inreok.getiserver` 바로 아래에는 `GetiServerApplication`(Application 진입점)과 `web`(공통 Web 기반) 두 항목만 있다.
- 금지 Package 검색(`controller`/`service`/`repository`/`entity`/`dto`/`exception`/`global`/`common` 등 Root 직속) 결과 **0건**(`git grep` 실측, 아래 "이번 PR 검증 결과" 참고).
- Spring Modulith `ModularityTest.verify()`는 순환 의존성과 경계 위반을 계속 검증하며, 현재 Module이 1개뿐이라 자명하게 통과한다(경계를 위반할 대상 자체가 없음).
- ArchUnit은 아직 도입하지 않았다. Spring Modulith가 이미 Module 경계를 검증하고 있고, 실제 여러 Domain Module이 생기기 전에는 ArchUnit이 검증할 구체적인 규칙(예: "Job Domain이 Member 내부 구현을 참조하지 않는다")이 존재하지 않는다. Notion Tech Stack(`Testing`)이 ArchUnit을 확정 도구로 명시하므로, 이 문서와 `docs/ai/coding-conventions.md`에 "실제 Domain Module이 2개 이상 되어 교차 참조 규칙이 필요해지는 시점에 도입"으로 상태를 갱신했다(아래 "이번 PR에서 반영한 변경" 참고).

## Configuration 및 Secret 검증

- `git grep`으로 Password/Secret/Token Literal, `.env` 추적 여부, 노출된 Discord Webhook URL 패턴을 검색한 결과 실제 위험 항목 **0건**.
- `.env`는 `.gitignore`에 의해 추적되지 않는다(`git ls-files`로 실측).
- `spring.jpa.hibernate.ddl-auto=validate`(공통 설정, 모든 환경), `spring.jpa.open-in-view=false`, `spring.flyway.clean-disabled=true`가 유지되고 있다.
- `application-prod.yaml`은 모든 값에 기본값이 없어 미지정 시 즉시 실패한다(PR 8에서 실측 확인).
- Discord CI 알림은 `DISCORD_CI_WEBHOOK_URL` Repository Secret으로만 참조하며, `gh secret list` 결과 현재 **미등록** 상태다(이름만 조회, 값 미확인).

## Docker/Persistence 검증

- `docker compose config --quiet` 성공(exit 0).
- PR 8, PR 9, PR 10에서 각각 `docker compose --profile app up -d --build`로 전체 Container 환경을 실제로 기동해 PostgreSQL/Redis 연결, Flyway Schema History 생성, Hibernate `ddl-auto=validate` 통과, Actuator Health `UP` 응답을 확인한 기록이 있다(각 PR의 Issue 댓글 참고).
- `integrationTest`(Testcontainers, PostgreSQL 18.4-alpine / Redis 8.8.1-alpine)는 이번 Audit에서도 다시 실행해 성공을 확인했다(아래 "이번 PR 검증 결과" 참고).

## Web/API 검증

- 공통 성공 응답 `ApiResponse<T> { data }`, 오류 응답 `ErrorResponse { code, message, status, path, timestamp, fieldErrors }`가 실제로 구현되어 있고 Web Slice Test(`GlobalExceptionHandlerTest` 10개, `WebCorsConfigTest` 2개)로 검증된다.
- **Notion API 명세서와의 불일치가 있다** — 세부 내용은 [`notion-repository-sync.md`](./notion-repository-sync.md)의 API Contract 항목을 따른다. 이번 PR에서 광범위한 Contract 변경은 하지 않았다.
- Pagination 최대 size 제한이 없던 것을 이번 PR에서 보완했다(아래 "이번 PR에서 반영한 변경" 참고).

## CI 및 Repository Policy 검증

- `.github/workflows/ci.yml`(Workflow 이름 `CI`)에 Wrapper Validation, Quality, Unit Test, Integration Test, Build, Docker Validation, Notify Discord 7개 Job이 구성되어 있다.
- `gh run list --branch develop --limit 3` 실측 결과 최신 `develop` Push Run(`30440403747`)이 `success`로 완료된 것을 확인했다.
- Required Status Check는 아직 Branch Protection에 적용하지 않았다(PR 10에서 의도적으로 보류, 사용자 승인 필요). 현재 `develop`/`main` 모두 Required Status Check 없음(`gh api .../branches/develop/protection` 실측).
- Ruleset은 없다(`gh api .../rulesets` → `[]`).
- CODEOWNERS는 Path별 실제 담당자 정보가 없어 도입하지 않았다(PR 10에서 결정, 유지).
- Dependabot(`gradle`, `github-actions` Ecosystem, weekly)이 구성되어 있다.

## 이번 PR 검증 결과 (실제 실행)

```text
./gradlew clean test build     BUILD SUCCESSFUL
./gradlew integrationTest      BUILD SUCCESSFUL (Docker 필요)
./gradlew koverHtmlReport      BUILD SUCCESSFUL
./gradlew koverXmlReport       BUILD SUCCESSFUL
docker compose config          exit 0
gh run list --branch develop   최신 Push Run success (Run 30440403747, 5m28s)
git grep(Secret/금지 Package)  0건
```

## 이번 PR에서 반영한 변경

실제 반영 내용은 Commit 목록과 [`notion-repository-sync.md`](./notion-repository-sync.md)의 "안전하게 반영한 항목"을 따른다. 요약:

- Pagination 최대 size(100) 서버 강제 추가(Notion API 명세서 확정 규칙 반영)
- ArchUnit/QueryDSL/Mockito 문서 상태를 "미정"에서 "Notion 확정, 실제 도입 시점 미정"으로 갱신(Dependency 자체는 추가하지 않음)
- Domain Module 내부 DDD Layer 원칙(domain/application/infrastructure/presentation) 문서화(Notion 컨벤션 확정 반영, 실제 Package는 아직 생성하지 않음)
- `AGENTS.md`에 Source of Truth 우선순위와 Notion 관계를 명시
- AI Agent Quick Start/작업 체크리스트 보완

## AI 개발 시나리오 Static Audit

4개 시나리오(북마크 기능/외부 API 연동/DB 필드 추가/모호한 버그)를 실제 Agent 실행 없이 현재 문서 기준으로 Static 분석했다. 상세 결과와 발견한 Gap, 실제 반영한 문서 변경은 [`ai-scenario-audit.md`](./ai-scenario-audit.md)를 따른다. 가장 중요한 발견은 "Domain 기능 작업 시 Notion을 먼저 확인하라"는 지시가 없었던 것이며, `docs/ai/workflow.md`에 반영해 해결했다.

## 이번 PR에서 반영하지 않은 항목(DECISION_REQUIRED)

다음은 제품/Architecture 대규모 변경에 해당해 사용자 결정 없이 임의로 변경하지 않았다. 전체 목록과 근거는 [`notion-repository-sync.md`](./notion-repository-sync.md)를 따른다.

- Kotlin → Java 전환 여부
- Root Package `team.inreok.getiserver` → `com.geti` 변경 여부
- API 공통 응답 Contract(`success`/`meta.requestId` 등) 변경 여부
- Git/Commit/Issue/PR Convention(영문 Scope Commit, `[Domain]` 제목) 전환 여부
