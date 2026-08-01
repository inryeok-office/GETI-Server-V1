# Claude 일일 자동 점검 Routine

Claude Code Routine(매일 정해진 시각에 자동 실행되는 Cloud Agent)이 GETI-Server의 `develop` Branch를 대상으로 수행하는 일일 회귀 점검의 기준을 정의하는 Canonical 문서다. 이 문서는 **Routine 사전 준비 문서**이며, 실제 Routine 생성·Schedule 활성화·Discord Webhook 등록은 다루지 않는다(사람이 직접 수행, [`사후 설정 체크리스트`](#사후-설정-체크리스트) 참고).

Routine은 별도 지시가 없어도 세션 시작 시 [`AGENTS.md`](../../AGENTS.md), [`CLAUDE.md`](../../CLAUDE.md), `.claude/rules/*.md`를 자동으로 로드한다. 이 문서는 그 공통 규칙을 반복하지 않고, **일일 자동 점검**이라는 실행 형태에서만 추가로 필요한 기준(무엇을 언제 어떻게 검증하고, 언제 Issue/PR을 만들고, 언제 멈추는지)만 다룬다. 규칙이 충돌하면 `AGENTS.md`의 우선순위(사용자 요청 > 현재 Issue > Notion 확정 요구사항 > 저장소 실제 구현 > `AGENTS.md` > 도구별 지침 > `docs/ai` 세부 정책 > 기존 코드 패턴)를 그대로 따른다.

## 목적

- 최신 `develop` 기준으로 매일 회귀(Regression)를 점검한다.
- 신규 기능을 개발하지 않는다.
- **구현이 완료된 도메인**(아래 [구현 상태](#도메인-구현-상태와-미구현-판단-기준) 참고)만 점검 대상으로 삼는다.
- 근거가 있고 재현 가능한 결함만 Issue로 만든다.
- `ai-fix-approved` Label이 붙은 **기존 Issue**만 자동으로 수정한다. Label이 없으면 Issue만 만들고 코드는 건드리지 않는다.
- 수정할 때는 `develop`을 대상으로 하는 Draft PR만 만든다. 자동 Merge는 하지 않는다.

## 실행 순서

```text
최신 develop 확인
→ 작업 트리 및 열린 Issue/PR 확인
→ 실제 존재하는 검증 명령 실행 ([확정한 검증 명령](#확정한-검증-명령) 참고)
→ 결함 재현
→ 기존 Issue/PR 중복 확인
→ 새 결함이면 Issue 최대 1개 생성
→ ai-fix-approved 라벨이 붙은 기존 Issue가 있으면 최소 수정
→ 검증
→ Draft PR 최대 1개 생성
→ 완료 보고
```

각 단계에서 판단할 수 없거나 중단 조건([작업 중단 조건](#작업-중단-조건) 참고)에 해당하면, 그 단계에서 멈추고 사람이 읽을 수 있는 보고만 남긴다. 억지로 다음 단계로 진행하지 않는다.

## 도메인 구현 상태와 미구현 판단 기준

`AGENTS.md`/`CLAUDE.md`가 명시하듯, 이 저장소는 최신 19개 Table ERD 기준 Domain Persistence 기반(JPA Entity, Repository, Flyway Migration)까지 구성되어 있고, 실제 Use Case(Service)·Controller는 일부 Domain에만 구현되어 있다. `src/main/kotlin/team/inreok/getiserver/domain/`에 Package가 존재해도 그 Domain의 Endpoint나 Service가 아직 없을 수 있다.

- Package가 존재하지만 Controller/Service가 없는 Domain: **미구현**으로 취급하고 결함으로 보고하지 않는다.
- Notion 기능명세서·API 명세서에 있지만 저장소에 아직 없는 기능: **미구현**으로 취급하고 결함으로 보고하지 않는다.
- 실제 구현된 Endpoint/Service가 명세나 기존 Test의 기대 동작과 다르게 동작하는 경우에만 결함으로 판단한다.

점검 시점의 실제 구현 범위는 `src/main/kotlin/team/inreok/getiserver/domain/*`의 실제 Package와 `README.md`/`docs/audit/foundation-audit.md`의 최신 상태 설명을 직접 확인해서 판단한다. 이 문서에 도메인별 구현 여부를 표로 고정하지 않는다 — 표는 금방 오래되고, 실제 코드가 항상 최신 기준이기 때문이다.

## Notion과 저장소가 다를 때

`docs/audit/notion-repository-sync.md`에 이미 정의된 분류 기준을 그대로 사용한다(새로 정의하지 않는다).

```text
MATCH                 Notion과 저장소가 일치
STALE_NOTION           Notion이 오래되었거나 실제 결정과 다름
IMPLEMENTATION_GAP    Notion 요구사항을 저장소가 아직 구현하지 않음
CONTRACT_MISMATCH     제품/기술 계약이 서로 다름
DECISION_REQUIRED     사람의 결정이 필요
```

- 이 분류는 자동 코드 수정을 허가하지 않는다. `CONTRACT_MISMATCH`/`DECISION_REQUIRED`로 분류되는 차이를 발견하면, 코드나 Notion 어느 쪽도 임의로 고치지 않고 보고만 한다.
- Notion에 접근할 수 없거나 정책을 확정할 근거가 부족하면, 그 항목의 판단을 보류하고 사유를 보고한다. Notion 접근 실패 자체를 제품 결함으로 보고하지 않는다.

## 확정한 검증 명령

Gradle Task 의존 관계상 `check`/`build`가 이미 `spotlessCheck`, `detekt`, `test`(`koverVerify` 포함)를 포함하므로, Routine은 아래 최소 명령 세트만 실행한다. 같은 Task를 여러 번 반복 실행하지 않는다.

```bash
./gradlew clean test build
```

이 한 번의 호출로 다음이 모두 실행된다.

- `spotlessCheck`(포맷), `detekt`(정적 분석)
- `test`(Unit Test, Web Slice Test, `ModularityTest`, `PackageArchitectureTest`, `OpenApiDocumentationTest` 포함 — 모두 `team.inreok.getiserver` 패키지 직속)
- `koverVerify`(현재 최소 기준 미설정으로 항상 통과)
- `build`(Jar Assemble 포함)

포맷이 깨져 있으면 `./gradlew spotlessApply`로 먼저 정리한 뒤 다시 `clean test build`를 실행한다.

### 선택 검증 (실행 가능한 환경에서만)

다음은 Docker Daemon이 필요하다. Claude Routine이 실행되는 Cloud 환경에 Docker Daemon이 있는지는 첫 수동 실행([사후 설정 체크리스트](#사후-설정-체크리스트) 9번)에서 실제로 확인해야 한다. 확인되기 전까지는 "실행 가능하면 실행, 실행할 수 없으면 건너뛰고 그 사실만 보고"로 취급한다.

```bash
./gradlew integrationTest        # PostgreSQL/Redis Testcontainers, Docker 필요
docker compose config --quiet    # Compose 구성 문법 검증
docker build -t ci-audit-check . # Image Build 검증(Registry Push 없음)
```

Docker Daemon 부재, Testcontainers 초기화 실패, Docker 관련 Network 제한으로 위 명령이 실행되지 않으면 **코드 결함으로 보고하지 않는다.** "실행하지 못한 검증"으로만 기록한다.

### 사용하지 않는 명령

`OpenApiDocumentationTest`, `ModularityTest`, `PackageArchitectureTest`를 별도로 격리 실행할 필요가 있으면(예: 실패 원인을 좁힐 때) 아래처럼 Class 이름으로 직접 필터링한다. 이미 `test`에 포함되어 있으므로 매일 반복 실행하는 기본 절차에는 포함하지 않는다.

```bash
./gradlew test --tests "team.inreok.getiserver.ModularityTest"
./gradlew test --tests "team.inreok.getiserver.PackageArchitectureTest"
./gradlew test --tests "team.inreok.getiserver.OpenApiDocumentationTest"
```

`koverHtmlReport`/`koverXmlReport`는 Coverage Report가 실제로 필요할 때만 별도로 실행한다(매일 자동 실행하지 않는다 — `check`에 포함되지 않고, 매번 산출물을 만들 필요가 없다).

## Issue 생성 조건

다음을 모두 만족해야 새 Issue를 만든다.

- 실제 구현된 기능의 명백한 결함이다.
- 테스트 또는 코드 경로로 재현 가능하다.
- 확정된 계약(Notion 확정 요구사항, 기존 Test의 기대 동작)에서 기대 동작을 확인할 수 있다.
- 같은 문제를 다루는 기존 Issue/PR이 없다(`gh issue list`, `gh pr list`로 확인).
- 외부 서비스 장애나 테스트 인프라 장애(Docker, Testcontainers, Network)가 원인이 아니다.
- 미구현 기능이 아니다.

## 자동 수정 가능 범위 / 불가 범위

`ai-fix-approved` Label이 붙은 **기존 Issue**에 한해서만 아래 "가능 범위" 내 수정을 시도한다. Label이 없는 Issue, 또는 지금 막 만든 새 Issue는 자동으로 수정하지 않는다(같은 실행에서 Issue를 만들고 바로 그 Issue를 고치지 않는다 — 사람이 Label을 붙이는 승인 단계를 항상 거친다).

### 가능 범위

- 명확한 Validation 누락
- 잘못된 HTTP Status
- 확정된 명세와 다른 단순 Endpoint 매핑
- 누락된 예외 변환
- 재현 가능한 Null 처리
- 기존에 확정된 권한 정책의 작은 구현 오류
- Swagger Annotation과 실제 DTO의 단순 불일치([`docs/ai/openapi-documentation.md`](../ai/openapi-documentation.md) 규칙 위반)
- 원인이 명확한 작은 회귀

### 불가 범위

- DB Migration 및 Schema 변경
- `SecurityConfig` 광범위 변경
- OAuth, JWT, Refresh Token 정책 변경
- 역할 및 권한 모델 변경
- API 계약 변경
- 도메인 경계 변경
- Entity 대량 추가·삭제
- 대규모 리팩터링
- 주요 Dependency 및 Framework 버전 변경
- 운영 환경변수 및 Secret 변경
- 배포·인프라 변경
- 외부 API 계약 변경
- 데이터 삭제·변환
- 정책 결정이 필요한 요구사항
- 재현되지 않는 추정성 문제
- 미구현 도메인 기능
- 단순 스타일 취향

불가 범위에 해당하는 문제를 발견하면 Issue만 만들고(조건을 만족하는 경우) `ai-fix-approved`를 붙이지 않는다 — Label 부여는 사람의 몫이다.

## 안전장치

- 실행당 새 Issue 최대 1개.
- 실행당 Draft PR 최대 1개.
- 변경 파일 최대 10개, 변경량 최대 300줄. 초과가 예상되면 코드를 고치지 않고 Issue만 만든다.
- `main`, `develop` 직접 Push 금지.
- 자동 Merge 금지.
- Force Push 금지.
- 검증(`clean test build`, 선택 검증)이 실패한 상태에서 Commit·Push·PR 생성 금지.
- Secret, Token, 개인정보를 로그·Issue·PR·Commit 어디에도 출력하지 않는다.
- 중복 Issue, 중복 PR 생성 금지(`gh issue list`, `gh pr list`로 먼저 확인).
- 작업 트리에 다른 사람의 미커밋 변경이 있거나, 이번 점검과 겹치는 파일을 다른 열린 PR이 다루고 있으면 수정을 중단하고 보고만 한다.
- 외부 서비스 장애(OAuth Provider, 사람인/고용24 등 외부 API)와 코드 결함을 구분한다.
- Testcontainers/Docker 인프라 실패와 코드 결함을 구분한다([선택 검증](#선택-검증-실행-가능한-환경에서만) 참고).

## Issue 및 Draft PR 형식

기존 Template을 그대로 재사용한다. 새 Template을 만들지 않는다.

- Issue: `.github/ISSUE_TEMPLATE/bug-report.yml`(재현 가능한 결함일 때) 형식을 기준으로 삼는다. 필요한 정보가 Template Field에 없으면 본문 안에 최소한으로 보완한다.
- PR: `.github/pull_request_template.md`를 그대로 채운다. Base Branch는 `develop`, 반드시 Draft로 생성한다.
- PR 제목: 저장소 Convention(`[FIX] 한글 요약` 등)을 따른다.
- PR 본문의 "연관 Issue"에 `Closes #{issue-number}`로 연결한다.
- Commit 메시지: `<type>: <한글 작업 내용>`([`docs/ai/git-conventions.md`](../ai/git-conventions.md), [`.claude/rules/git-and-github.md`](../../.claude/rules/git-and-github.md) 참고).

## Discord 알림 규칙 (선택, 문서화만)

Discord 알림은 선택 기능이며, 이 PR과 이 문서는 **실제 Webhook을 만들거나 등록하지 않는다.** 나중에 사람이 필요하다고 판단하면 아래 기준으로 Routine의 실행 환경(Claude Routine의 자체 환경변수 — GitHub Actions Secret인 `DISCORD_CI_WEBHOOK_URL`과는 별개의 값/등록 위치일 수 있다)에 `DISCORD_CI_WEBHOOK_URL`을 등록한다.

- 환경변수(`DISCORD_CI_WEBHOOK_URL`)가 없으면 조용히 건너뛴다(Routine 실행 자체를 실패시키지 않는다).
- 점검 시작 1회, 종료 1회만 전송한다(단계마다 전송하지 않는다).
- 동적 값(Branch, 결과 요약 등)은 반드시 `jq --arg`로 Escape해서 Payload를 만든다. Shell 문자열 결합으로 JSON을 직접 만들지 않는다.
- `allowed_mentions.parse`는 빈 배열로 고정한다.
- Connect Timeout 10초, 전체 Timeout 20초, 재시도 없음(1회 시도).
- 전송 실패가 전체 점검 실행 자체를 실패로 만들지 않는다(Non-blocking).
- Webhook URL, Secret, Token, Stack Trace, Diff 원문을 메시지에 포함하지 않는다.
- Webhook URL을 이 저장소의 어떤 파일에도 실제 값으로 작성하지 않는다.

이 정책은 `.github/workflows/ci.yml`의 `notify-discord` Job이 이미 따르는 것과 동일한 기준이다([`docs/development/ci.md`](../development/ci.md)의 "Discord CI 알림" 참고). Routine이 실제로 Discord 알림을 보내려면 별도로 Webhook을 발급하고 Routine 환경에 등록해야 하며, 이번 PR은 그 등록을 수행하지 않는다.

## 작업 중단 조건

다음 상황에서는 임의로 해결하지 말고 실행을 중단한 뒤 보고한다.

- 사람의 미커밋 변경과 충돌한다.
- 같은 목적의 열린 Issue 또는 PR이 이미 있다.
- 최신 `develop` 동기화(`git fetch`, `git pull --ff-only`)에 실패한다.
- GitHub 인증이 없거나 Issue/PR 생성 권한이 없다.
- 기존 하네스 문서(`AGENTS.md`, `CLAUDE.md`, `.claude/rules/*`, `docs/ai/*`) 사이에 정책 충돌이 있다.
- Notion 내용을 확인해야 판단할 수 있는데 확인할 수 없다.
- 제품 코드를 바꾸지 않고는 목표를 달성할 수 없다(범위를 넘어서는 변경이 필요하다).
- 새 Dependency나 Gradle Plugin 추가가 필요하다.
- DB Schema, Security, OAuth, JWT 변경이 필요하다.
- 검증(`clean test build`, 선택 검증)이 실패했는데 원인이 이번 점검 범위를 넘어선다.

중단할 때는 지금까지 확인한 내용, 중단 원인, 사람이 해야 할 조치를 구체적으로 남긴다.

## 최종 Routine 프롬프트

Claude Routine 생성 화면의 Prompt 입력란에 아래 내용을 그대로 붙여넣는다. 이 프롬프트는 이 문서를 Canonical 기준으로 참조하며, 문서가 갱신되면 Routine의 동작도 함께 갱신된다(프롬프트 자체를 다시 등록할 필요 없음).

```text
너는 GETI-Server(inryeok-office/GETI-Server) 저장소의 일일 자동 회귀 점검을 수행한다.

가장 먼저 저장소의 docs/automation/claude-daily-audit-routine.md를 읽고, 그 문서에 정의된
실행 순서·검증 명령·Issue 생성 조건·자동 수정 가능/불가 범위·안전장치·작업 중단 조건을
그대로 따른다. AGENTS.md, CLAUDE.md, .claude/rules/*의 공통 규칙도 항상 함께 지킨다.

핵심 제약(문서와 다르게 읽히더라도 아래를 우선한다):
- 신규 기능을 개발하지 않는다. 최신 develop 기준 회귀 점검만 한다.
- main, develop에 직접 Push하지 않는다. Force Push하지 않는다. 자동 Merge하지 않는다.
- 실행당 새 Issue 최대 1개, Draft PR 최대 1개만 만든다.
- ai-fix-approved Label이 붙은 기존 Issue가 아니면 코드를 수정하지 않는다. 새로 만든
  Issue를 같은 실행에서 바로 고치지 않는다.
- 변경 파일 10개, 변경량 300줄을 넘길 것으로 예상되면 코드를 고치지 않고 Issue만 만든다.
- 검증(./gradlew clean test build)이 실패한 상태에서는 Commit/Push/PR을 만들지 않는다.
- 미구현 도메인 기능과 Notion에만 있고 저장소에 없는 기능을 결함으로 보고하지 않는다.
- Notion과 저장소가 다르면 docs/audit/notion-repository-sync.md의 분류만 하고,
  어느 쪽도 임의로 고치지 않는다.
- Docker/Testcontainers를 이 환경에서 실행할 수 없으면 코드 결함으로 취급하지 않고
  "실행하지 못한 검증"으로만 기록한다.
- Secret, Token, 개인정보를 어떤 출력에도 포함하지 않는다.
- 판단할 수 없거나 충돌이 있으면 임의로 진행하지 말고 실행을 중단한 뒤 보고한다.

실행이 끝나면 다음을 요약해서 보고한다: 실행한 검증과 결과, 발견한 문제(있다면),
생성한 Issue/PR(있다면, 번호와 URL), 실행하지 못한 검증과 이유, 중단했다면 그 사유.
```

## 사후 설정 체크리스트

이 PR이 Merge된 뒤 **사람이 직접** 수행해야 하는 절차다. Routine 생성이나 Schedule 활성화는 이 저장소 작업이 자동으로 하지 않는다.

```text
1. Claude와 GitHub 연결 확인
2. GETI-Server Repository만 접근 허용
3. Claude Routines에서 새 Routine 생성
4. 위 "최종 Routine 프롬프트"를 그대로 입력
5. Repository로 inryeok-office/GETI-Server 선택
6. Asia/Seoul 기준 매일 07:00로 Schedule 설정
7. 불필요한 Connector 제거
8. Schedule을 일시정지 상태로 둔다(바로 활성화하지 않는다)
9. Run now로 첫 수동 실행 — 이 실행에서 Docker Daemon 사용 가능 여부를 함께 확인한다
   (사용 불가하면 이 문서의 "선택 검증" 절이 실제로도 건너뛰어지는지 확인)
10. 실행 세션 상세(무엇을 실행했는지, Issue/PR을 만들었는지, 중단했는지)를 직접 확인
11. 정상 동작하면 Schedule 활성화
12. 2~3회 안정화된 뒤 선택적으로 Discord Webhook 추가([위 절](#discord-알림-규칙-선택-문서화만) 참고)
```

## 관련 문서

- [`AGENTS.md`](../../AGENTS.md) — AI Agent 공통 규칙과 우선순위
- [`docs/ai/README.md`](../ai/README.md) — AI 개발 문서 진입점
- [`docs/ai/testing-policy.md`](../ai/testing-policy.md), [`docs/development/testing.md`](../development/testing.md) — 테스트 정책 상세
- [`docs/development/persistence.md`](../development/persistence.md) — `integrationTest`가 `check`/`build`에서 분리된 이유
- [`docs/development/ci.md`](../development/ci.md) — GitHub Actions CI, Discord CI 알림 정책
- [`docs/ai/openapi-documentation.md`](../ai/openapi-documentation.md) — Swagger/OpenAPI 문서화 필수 규칙
- [`docs/audit/notion-repository-sync.md`](../audit/notion-repository-sync.md) — Notion-저장소 차이 분류 기준
- [`docs/ai/git-conventions.md`](../ai/git-conventions.md), [`.claude/rules/git-and-github.md`](../../.claude/rules/git-and-github.md) — Branch/Commit/PR/Label 규칙
