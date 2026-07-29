# Notion ↔ Repository 동기화 보고서

GETI Notion(루트: `GETI` 페이지)과 이 저장소의 실제 구현을 대조한 결과다. 확인한 Notion 페이지, 읽지 못한 항목, 각 불일치의 상태와 권장 조치를 기록한다. **이 문서는 Notion을 수정하지 않는다.** 실제 반영 여부는 사용자 결정이 필요하다.

## 확인한 Notion 페이지

| 페이지 | 확인 여부 | 비고 |
| --- | --- | --- |
| GETI(루트) | 확인 | 하위 문서 목록만 포함 |
| 프로젝트 설명서 | 확인 | 전체 읽음 |
| 기능명세서 | 확인 | 16개 Domain Database 목록 확인(각 행 상세는 미조회, 아래 참고) |
| API 명세서 | 확인 | Common Response, Enum 24개, API 목록(14개 Resource) 확인 |
| GETI PRD | 확인 | 전체 읽음(21개 Section) |
| BE / 컨벤션 | 확인 | 전체 읽음(28개 Section) |
| BE / Tech stack | 확인 | 전체 읽음 |
| BE / 도메인 | 확인 | "도메인 목록" Database 16개 행 전부 조회(도메인/설명/주요 기술) |
| BE / 환경변수 | 확인 | **비어 있음**(빈 YAML Code Block) |
| DevOps | 확인 | **비어 있음** |
| AI | 확인 | **비어 있음**(Blank Page) |

## 읽지 못한 항목

- 기능명세서의 16개 Domain Database 각 행(개별 기능 상세, 수용 기준 등)은 Database 목록만 확인했고 행 단위 상세는 조회하지 않았다. 실제 Domain 기능 구현 PR에서 해당 Domain Database를 직접 조회해야 한다.
- API 명세서의 14개 Resource별 Database(`/api/v1/auth` 등)의 개별 Endpoint 행도 목록만 확인하고 상세는 조회하지 않았다.
- FE, App, Design 페이지는 이번 PR의 확인 대상(프로젝트 설명서/기능명세서/API 명세서/PRD, BE 컨벤션/Tech Stack/도메인/환경변수, DevOps, AI)에 포함되지 않아 조회하지 않았다.
- Notion Database 조회 권한이나 사용량 제한으로 숨겨진 항목은 없었다(모두 정상 응답).

## 상태 값 정의

```text
MATCH                 Notion과 저장소가 일치
STALE_NOTION          Notion이 오래되었거나 실제 결정과 다름(저장소 기준 유지 권장)
IMPLEMENTATION_GAP    Notion 요구사항을 저장소가 아직 구현하지 않음(예정된 범위)
CONTRACT_MISMATCH     제품/기술 계약이 서로 다름(임의로 한쪽을 따르지 않음)
DECISION_REQUIRED     사용자 결정이 필요(대규모 변경 대상)
```

## 종합 대조표

| 항목 | Notion | Repository | 상태 | 권장 기준 | 조치 |
| --- | --- | --- | --- | --- | --- |
| Language | Java 25 LTS · Python | Kotlin 2.3.21(Java Toolchain 25) | **DECISION_REQUIRED** | 아래 상세 참고 | 사용자 결정 필요 |
| Root Package | `com.geti`(컨벤션 예시) | `team.inreok.getiserver`(PR 15 확정) | **DECISION_REQUIRED** | 아래 상세 참고 | 사용자 결정 필요 |
| Spring Boot | 4.1 | 4.1.0 | MATCH | 유지 | 없음 |
| Architecture | Modular Monolith · Spring Modulith · DDD | Spring Modulith(Module 1개), DDD 내부 Layer 미도입 | IMPLEMENTATION_GAP | Notion DDD Layer 채택 | 이번 PR에서 문서화(아래 참고) |
| Build | Gradle Kotlin DSL | Gradle Kotlin DSL | MATCH | 유지 | 없음 |
| API | REST · OpenAPI · Swagger UI | REST(Web 기반만), OpenAPI 미도입 | IMPLEMENTATION_GAP | 첫 Domain Controller 시점 재검토 | PR 9에서 이미 보류 사유 문서화됨(유지) |
| Auth | Spring Security · OAuth2 · JWT · RBAC | 미구현 | IMPLEMENTATION_GAP | 향후 Auth Domain PR | 이번 범위 아님 |
| ORM | Spring Data JPA · Hibernate · QueryDSL | Spring Data JPA · Hibernate(QueryDSL 없음) | IMPLEMENTATION_GAP | QueryDSL은 실제 복잡한 조회가 생기는 시점 | 문서 상태만 "확정, 도입 시점 미정"으로 갱신 |
| Database | PostgreSQL 18 | PostgreSQL 18.4(compose.yaml) | MATCH | 유지 | 없음 |
| Migration | Flyway | Flyway | MATCH | 유지 | 없음 |
| Cache | Redis | Redis(Lettuce) | MATCH | 유지 | 없음 |
| Search | Elasticsearch · Nori | 미구현(compose.yaml에 없음) | IMPLEMENTATION_GAP | Search Domain PR에서 도입 | 이번 범위 아님 |
| Object Storage | MinIO · S3 Compatible | MinIO(compose.yaml) | MATCH | 유지 | 없음 |
| Testing | JUnit 5 · Mockito · Testcontainers · REST Assured · ArchUnit · Spring Modulith Test | JUnit 5 · Testcontainers · Spring Modulith Test | IMPLEMENTATION_GAP | 아래 상세 참고 | 문서 상태 갱신(이번 PR) |
| CI/CD | GitHub Actions · GHCR | GitHub Actions(CI만, Registry Push 없음) | IMPLEMENTATION_GAP | CD는 별도 PR | 이번 범위 아님(의도적 제외) |
| Monitoring | Actuator · Micrometer · Prometheus · Grafana | Actuator(`health`만) | IMPLEMENTATION_GAP | Observability 전용 PR | 이번 범위 아님 |
| Logging/Tracing | Loki · OpenTelemetry · Grafana Tempo | 미구현 | IMPLEMENTATION_GAP | Observability 전용 PR | 이번 범위 아님 |
| API 공통 응답 | `{success, data, meta.requestId}` / `{success:false, error:{code,message,fieldErrors}, meta}` | `{data}` / `{code,message,status,path,timestamp,fieldErrors}` | **CONTRACT_MISMATCH** | 아래 상세 참고 | 사용자 결정 필요, 이번 PR 미변경 |
| Pagination | `page=0, size=20, 최대 size=100` | `page=0, size=20`(최대 제한 없었음) | IMPLEMENTATION_GAP | Notion 기준 채택 | **이번 PR에서 반영**(아래 참고) |
| Git Branch | `feature/{n}-{domain}-{feature}`, `fix/`, `refactor/`, `chore/`, `release/vX.Y.Z`, `hotfix/` | `chore/{n}-{설명}`, `refactor/{n}-{설명}`(feature/fix/release/hotfix 미사용, 아직 기능 개발 전이라 자연스러움) | STALE_NOTION 가능성 | 아래 상세 참고 | 사용자 결정 필요 |
| Issue/PR 제목 | `[Domain] 작업 내용` | `[TYPE] 작업 내용`(예: `[CHORE]`) | STALE_NOTION 가능성 | 아래 상세 참고 | 사용자 결정 필요 |
| Commit | `type(scope): subject`(영문) | `type: 한글 설명`(Scope 없음) | STALE_NOTION 가능성 | 아래 상세 참고 | 사용자 결정 필요 |
| Merge 방식 | Squash and merge 기본 | 저장소 설정상 Merge/Squash/Rebase 모두 허용(제한 없음) | IMPLEMENTATION_GAP | Squash 기본 채택 권장 | Repository 설정 변경은 사용자 승인 필요(이번 PR 미변경) |
| PR 승인 수 | 최소 1명 | 1명(Branch Protection 실측) | MATCH | 유지 | 없음 |
| Domain 목록 | Auth·Member·Company·Job·Collector·Search·AI(Analysis)·Recommendation·Form·Application·Program·Portfolio·Notification·Inquiry·File·Audit·Scheduler(17, 페이지별 표기 약간 다름) | 미구현(Domain Package 없음) | IMPLEMENTATION_GAP | 아래 Domain Map 참고 | 문서화만(이번 PR), Package 생성 안 함 |
| 환경변수 문서 | 비어 있음 | `.env.example`, `docs/development/configuration.md`, `docs/development/persistence.md`, `docs/development/web-api.md`에 실제 목록 존재 | STALE_NOTION | 저장소 기준 유지 | Notion 채우기는 사용자 결정(자동 수정 안 함) |
| DevOps 문서 | 비어 있음 | `docs/development/docker.md`, `docs/development/ci.md` 존재 | STALE_NOTION | 저장소 기준 유지 | Notion 채우기는 사용자 결정 |
| AI 문서 | 비어 있음 | `docs/ai/*`, `.claude/*`, `.codex/*` 존재 | STALE_NOTION | 저장소 기준 유지 | Notion 채우기는 사용자 결정 |

## DECISION_REQUIRED 상세

### 1. Language: Kotlin vs Java

Notion Tech Stack의 "Core" 표는 `Language: Java 25 LTS · Python`을 명시한다. 그러나 "도메인 목록" Database의 "주요 기술" Multi-Select 옵션에는 `Java`와 `Kotlin`이 모두 존재하며(다른 Notion 편집자가 Kotlin도 후보로 고려한 정황), 실제 이 저장소는 PR 1부터 지금까지 100% Kotlin(`.kt`)으로 구현되어 있다(Kotlin Gradle Plugin, ktlint/Spotless, detekt, `kotlin-reflect`, `jackson-module-kotlin`, Kotlin JPA Plugin 등 전체 Toolchain이 Kotlin 전제).

**이번 PR에서 Kotlin을 Java로 변환하지 않았다**(절대 안전 규칙에 따라 금지됨). 근거:
- 10개 PR, 8개 Production Class, 12개 Test Class가 이미 Kotlin으로 구현되어 있다.
- `AGENTS.md`, `CLAUDE.md`, `docs/ai/*`, `.claude/*` 전체가 Kotlin 전제로 작성되어 있다.
- Java로 전환하려면 사실상 PR 1~10 전체를 다시 구현해야 한다.

**사용자 결정이 필요한 질문**: Notion의 "Java 25 LTS"가 (a) 이 저장소가 시작되기 전에 작성된 오래된 계획이고 실제로는 Kotlin으로 진행하기로 이미 합의된 것인지, (b) 여전히 유효한 목표이며 이 저장소가 잘못된 언어로 시작된 것인지. (b)라면 언어 전환은 별도의 대규모 Migration Issue로 처리해야 하며 이번 PR로 처리할 수 없다.

### 2. Root Package: `team.inreok.getiserver` vs `com.geti`

Notion 컨벤션 문서의 "11. Package Convention"은 `com.geti`를 Root Package로 예시를 든다. 이 저장소는 PR 15("패키지 구조 및 모듈 경계 정리")에서 `team.inreok.geti.getiserver`(Spring Initializr의 group+artifact 자동 결합으로 생긴 중복 표기)를 `team.inreok.getiserver`로 정리했으며, `com.geti`로의 변경은 그 PR에서도 검토되지 않았다.

**이번 PR에서 Root Package를 변경하지 않았다.** 근거:
- Root Package 변경은 모든 Kotlin 파일, `docs/architecture/modularity.md`, `docs/ai/*`, `.claude/*` 전체에 영향을 준다.
- `com.geti`가 실제 조직/도메인 소유권을 반영하는지(예: 실제 도메인 이름 `geti.io` 등) 확인할 수 없었다.
- Spring Modulith의 Application Module 자동 탐지(Root Package 바로 아래 Package를 Module로 인식)가 Root Package 변경에 민감하므로, 변경 시점은 실제 Domain Module이 생기기 전인 지금이 가장 비용이 낮다는 점은 참고할 만하다.

**사용자 결정이 필요한 질문**: `com.geti`로 변경할지, 현재 `team.inreok.getiserver`를 유지할지. 변경한다면 이 PR이 아니라 별도의 단일 목적 Refactor PR(PR 15와 동일한 패턴)로 처리하는 것을 권장한다.

### 3. API 공통 응답 Contract

Notion API 명세서:
```json
{ "success": true, "data": {}, "meta": { "requestId": "UUID" } }
```
```json
{ "success": false, "error": { "code": "ERROR_CODE", "message": "...", "fieldErrors": [] }, "meta": { "requestId": "UUID" } }
```

저장소(PR 9, `team.inreok.getiserver.web`):
```json
{ "data": {} }
```
```json
{ "code": "...", "message": "...", "status": 400, "path": "...", "timestamp": "...", "fieldErrors": [] }
```

차이점: `success` Boolean 필드 유무, 오류가 `error` 하위에 중첩되는지 여부, `meta.requestId` 유무(PRD 12.3 "요청별 requestId를 생성해 응답·로그·트레이스에서 연결한다"와 연결된 요구사항으로 보임), `status`/`path`/`timestamp`가 Notion 명세에는 없음.

**이번 PR에서 API Contract를 변경하지 않았다.** 근거: 절대 안전 규칙("제품 계약을 임의로 바꾸지 않는다") 및 PR 9가 이미 실제 Web Slice Test 10개로 현재 Contract를 검증하고 있어, 이를 되돌리면 기존 Test와 문서를 대규모로 다시 작성해야 한다.

**사용자 결정이 필요한 질문**: Notion 명세대로 `success`/`error`/`meta.requestId` 구조로 변경할지, 현재 구조를 유지하고 Notion 문서를 갱신할지. `requestId`는 아직 Trace/Correlation ID 인프라가 없어(Observability 범위, PR 9에서 의도적으로 보류) 어느 쪽을 택하든 실제 값을 채우려면 후속 작업이 필요하다.

### 4. Git/Commit/Issue/PR Convention

Notion 컨벤션은 영문 Commit(`type(scope): subject`)과 `[Domain]` Issue/PR 제목을 명시한다. 이 저장소는 `AGENTS.md`/`CLAUDE.md`/`docs/ai/git-conventions.md`/`.claude/rules/git-and-github.md`에 명시된 대로 한글 Commit(`type: 한글 설명`, Scope 없음)과 `[TYPE]` Issue/PR 제목을 PR 1부터 PR 10까지 총 10개 PR, 수십 개 Commit에서 일관되게 사용했다. 이 지침은 이번 세션 전체에서 사용자가 각 PR 작업 지시마다 반복해서 명시한 내용이기도 하다.

**이번 PR에서 Git Convention을 변경하지 않았다.** 근거: 현재 저장소 우선순위(§ Source of Truth, 아래 참고)에서 "현재 사용자 명시 지시"와 "Repository의 실제 Build·Test·Code(이미 확립된 패턴)"가 Notion보다 상위이며, 이미 10개 PR이 한글 Commit으로 병합되어 있어 지금 영문으로 전환하면 History 내에서 언어가 섞이게 된다.

**사용자 결정이 필요한 질문**: Notion 컨벤션이 실제 팀 표준이라면 (a) 이번 시점부터 영문 Commit + `[Domain]` 제목으로 전환하고 Notion을 Source of Truth로 삼을지, (b) 현재 한글 Commit 방식이 이 프로젝트의 실제 표준이므로 Notion 컨벤션 문서를 갱신해야 하는지. Branch Naming의 `{domain}` 조각(`feature/12-auth-dg-oauth-login`)은 실제 Domain 개발이 시작되면 자연스럽게 채택할 수 있어 보이며 큰 충돌은 아니다.

## Domain Map (문서화만, Package 미생성)

Notion 3곳(기능명세서 Database 목록, Tech Stack "Module" 목록, 도메인 목록 Database)에서 확인한 Domain 이름을 통합한 결과다. 실제 Package는 만들지 않았다.

| Domain | 기능명세서 | Tech Stack Module | 도메인 목록 DB | 설명(도메인 목록 DB 기준) |
| --- | --- | --- | --- | --- |
| Auth | O | O | O | DG OAuth, 교직원 Google OAuth, 로그인, 토큰 발급 |
| Member | O | O | O | 회원, 다중 역할, 교직원 승인, 학생 프로필 |
| Company | O | O | O | 기업 정보, MOU, 협약 관계 |
| Job | O | O | O | 채용공고 작성/게시/수정/마감/북마크/조회수 |
| Collector | O | O | O | 외부 채용 API 수집, 하루 1회 동기화 |
| Search | O | O | O | Elasticsearch 기반 공고 검색/필터/정렬 |
| AI Analysis(Tech Stack에는 "AI") | O | O(이름 "AI") | O(이름 "AI Analysis") | 공고 요약, 기술 추출, 적합성 분석 |
| Recommendation | O | O | O | 추천 점수, 적합도, 관심 없음 관리 |
| Form | O(API 명세서 `/api/v1/forms`, PRD 6.9) | O | **미포함**(Application 설명에 "동적 신청서 양식" 포함) | 교사용 신청 양식 생성/복제 |
| Application | O | O | O | 신청서 작성, 지원, 수정 요청, 승인/거부 |
| Program | O | O | O | 특강/취업 프로그램, 선착순 신청, 정원 |
| Portfolio | O | O | O | 포트폴리오 수합 요청/제출/현황 |
| Notification | O | O | O | 인앱 알림, Discord 공고 전송 |
| Inquiry | O | O | O | 문의 등록, Discord Webhook 전달 |
| File | O | O | O | 첨부파일 업로드/다운로드/검증 |
| Audit | O | O | O | 감사 로그, 민감정보 마스킹 |
| Scheduler | O | O | O | 수집/추천/마감/알림 정기 작업 |

Notion 내부에서도 Form이 별도 Domain인지 Application에 포함되는지 3개 문서가 서로 다르게 표기한다(기능명세서·Tech Stack은 별도, 도메인 목록 DB는 Application에 통합). 실제 Form Domain PR을 시작하기 전에 이 부분도 함께 확인이 필요하다.

## 안전하게 반영한 항목 (이번 PR)

다음은 대규모 제품/Architecture 결정이 아니고, Notion에 명확히 확정되어 있으며, 저장소 기존 방향과 충돌하지 않아 이번 PR에서 직접 반영했다.

1. **Pagination 최대 size=100 서버 강제** — `PageableHandlerMethodArgumentResolverCustomizer` Bean 추가(`team.inreok.getiserver.web`). Notion API 명세서 "목록 기본값: page=0, size=20, 최대 size=100"을 반영한다.
2. **QueryDSL/ArchUnit/Mockito 문서 상태 갱신** — `docs/ai/coding-conventions.md`, `docs/ai/testing-policy.md`의 "아직 확정되지 않은 규칙/도구" 목록에서 세 항목을 제거하고 "Notion Tech Stack에서 확정, 실제 Dependency 추가는 필요 시점에"로 명시했다. **실제 Dependency는 추가하지 않았다**(사용할 구체적인 코드가 아직 없음).
3. **Domain Module 내부 DDD Layer 원칙 문서화** — Notion 컨벤션의 domain/application/infrastructure/presentation 4-Layer 원칙을 `docs/architecture/modularity.md`에 "실제 Domain Module이 추가될 때 적용할 내부 구조"로 문서화했다. `docs/ai/coding-conventions.md`의 "아직 확정되지 않은 규칙"에서도 이 항목을 제거했다. **실제 Package는 생성하지 않았다.**
4. **`AGENTS.md`에 Source of Truth 우선순위 명시** — 저장소 문서 간 충돌 시 우선순위(사용자 지시 > 확정 제품 요구사항/API 계약 > 실제 Build/Test/Code > `AGENTS.md` > Architecture 문서 > `CLAUDE.md`/`.claude` > `docs/ai`/`docs/development` > Codex 정책 > Notion 초안 > 개인 취향)를 명시했다.

## 다음 단계 (사용자 결정 필요)

1. Language(Kotlin 유지 vs Java 전환)와 Root Package(`team.inreok.getiserver` 유지 vs `com.geti`) 결정
2. API 공통 응답 Contract(현재 구조 유지 vs Notion 구조 채택) 결정, 필요하면 별도 PR로 Contract Migration 진행
3. Git/Commit/Issue/PR Convention(현재 한글 방식 유지 vs Notion 영문 방식 전환) 결정
4. 위 결정 결과를 Notion 또는 저장소 중 실제 기준이 되는 쪽에 반영(Notion 수정은 사용자가 직접 수행하거나 별도 요청)
5. Form Domain이 Application에 통합되는지 별도 Domain인지 Notion 내부 정리
