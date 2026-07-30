# 최신 최소 ERD (19개 Table)

GETI의 실제 Domain Persistence 기반(Entity, Repository, Flyway Migration)이 이 문서가 설명하는 최신 최소 ERD를 기준으로 구성되어 있다. 과거 27개 Table 설계는 폐기되었고 이 저장소에 존재하지 않는다. 정확한 Column, 제약조건, Index는 [`V2__create_core_domain_schema.sql`](../../src/main/resources/db/migration/V2__create_core_domain_schema.sql)이 최종 근거이며, 이 문서는 그 구조를 사람이 읽기 쉽게 요약한다.

## 이번 범위와 제외 범위

이번 Persistence 기반 PR은 19개 Table의 JPA Entity, Spring Data Repository, Flyway Migration, Testcontainers Integration Test를 구성한다. 다음은 포함하지 않는다.

```text
OAuth 전체 Flow(DG/Google 실제 연동), JWT 발급/재발급 API
Application/Presentation Layer(Use Case Service, Controller, Request/Response DTO)
채용 공고 수집기, AI 분석 호출, 추천 알고리즘
MinIO 업로드, Discord 전송, Elasticsearch 색인, 비동기 Worker
```

## Domain Module과 Table 배치

각 Domain Module은 `team.inreok.getiserver.domain` 바로 아래 독립 Package(`domain.{domain-name}`)이며, 현재는 `entity`(Entity, 필요하면 `entity/type`에 Enum)와 `repository`(Spring Data JPA Repository Interface)만 존재한다. 실제 Service/Controller가 생기기 전까지 그 이상의 Sub-package는 만들지 않는다([`modularity.md`](./modularity.md) "Domain Package 내부 구조" 참고).

| Domain | Table | 비고 |
| --- | --- | --- |
| `member` | `members`, `member_roles` | 회원, 내부 역할(다대다) |
| `auth` | `refresh_tokens` | GETI 자체 Refresh Token |
| `file` | `files` | MinIO 객체의 메타데이터(Entity 이름은 `StoredFile`, `java.io.File`와의 이름 충돌 회피). 실제 바이너리는 DB에 저장하지 않는다 |
| `company` | `companies` | 기업/MOU 정보 |
| `job` | `jobs` | 채용 공고 |
| `ai` | `job_ai_analyses` | 공고 AI 분석(공유 PK) |
| `recommendation` | `member_job_preferences`, `recommendations` | 회원 채용 선호와 맞춤 추천 |
| `application` | `job_applications` | 내부 지원 |
| `program` | `programs`, `program_applications` | 취업 프로그램과 신청 |
| `portfolio` | `portfolio_requests`, `portfolio_submissions` | 포트폴리오 제출 요청과 제출 |
| `notification` | `notifications` | 인앱 알림 |
| `inquiry` | `inquiries` | 문의와 Discord 전달 결과 |
| `collector` | `job_collection_runs` | 공고 수집 실행 이력(아래 "collector가 operation의 Enum을 재사용하는 이유" 참고) |
| `operation` | `async_operations` | 비동기 작업 |
| `audit` | `audit_logs` | 감사 로그 |

### collector가 operation의 Enum을 재사용하는 이유

`job_collection_runs.status`와 `async_operations.status`가 ERD상 동일한 `operation_status` Enum(`PENDING`/`PROCESSING`/`COMPLETED`/`FAILED`) 값 집합을 쓴다. `collector`와 `operation`을 서로 다른 Domain으로 유지하면서 이 Enum을 중복 정의하지 않기 위해, `operation.entity.type` Package를 Spring Modulith Named Interface(`@NamedInterface("type")`)로 공개하고 `collector`가 `operation.entity.type.OperationStatus`를 참조하도록 허용했다. 자세한 구현과 근거는 [`modularity.md`](./modularity.md)의 "Domain 간 허용 의존"을 따른다. `./gradlew test --tests "*ModularityTest"`로 이 의존이 다른 비공개 참조 없이 통과하는지 확인했다.

## JPA 연관관계 전략

Domain 경계를 넘는 FK(예: `jobs.company_id`, `job_applications.applicant_member_id`)는 JPA 연관관계(`@ManyToOne` 등)로 만들지 않고 평범한 `Long`/`UUID` Column으로 보관한다. 같은 Domain 안의 FK(예: `member_roles.member_id`)도 동일하게 ID Column으로 다뤄 불필요한 양방향 연관관계와 `EAGER`, 광범위한 `CascadeType.ALL`을 만들지 않았다. 그 결과 Entity 사이에는 Java/Kotlin 타입 의존성이 (collector→operation의 명시적 Enum 참조를 제외하면) 전혀 없고, `ModularityTest`(`modules.verify()`)가 통과한다. DB 수준의 참조 무결성(FK 제약, CASCADE/SET NULL)은 Migration이 담당한다.

복합키와 공유 PK는 다음과 같이 구현했다.

- `member_roles`: `@EmbeddedId MemberRoleId(memberId, role)`
- `member_job_preferences`: `@EmbeddedId MemberJobPreferenceId(memberId, jobId)`
- `job_ai_analyses`: `job_id`를 `@GeneratedValue` 없이 그대로 `@Id`로 사용하는 공유 PK(같은 값을 가진 `jobs.id`를 Application에서 대입)
- `async_operations.id`: `@GeneratedValue` 대신 Hibernate `@UuidGenerator`로 UUID를 Application에서 미리 생성

## 시간 타입과 Timestamp 자동화

ERD가 명시한 대로 PostgreSQL `timestamp`(Time Zone 없음) + Kotlin `LocalDateTime`을 사용한다. 아직 저장소에 공용 BaseEntity/Auditing 표준이 없고(`docs/ai/coding-conventions.md`의 "아직 확정되지 않은 규칙" 참고), 19개 Table의 Timestamp Column 구성이 균일하지 않아(예: `files`는 `updated_at`이 없고, `job_ai_analyses`는 `requested_at`/`completed_at`이라는 고유한 이름을 쓴다) 새 BaseEntity 추상화를 도입하지 않고 각 Entity에 Hibernate `@CreationTimestamp`/`@UpdateTimestamp`를 필요한 곳에만 직접 붙였다.

## JSONB Mapping

`majors`, `skills`, `required_skills`, `answers`, `target_condition`, `changed_data` 등 `jsonb` Column은 Kotlin `String`(원문 JSON 텍스트) 속성에 `@JdbcTypeCode(SqlTypes.JSON)` + `@Column(columnDefinition = "jsonb")`를 붙여 매핑한다. Hibernate 6/7의 내장 기능만 사용하며(추가 Library 없음), `String` 속성은 이미 유효한 JSON 문자열이라는 전제로 그대로 저장/조회된다. 실제 구조화된 파싱/검증은 Application 계층이 생기는 시점에 판단한다.

## 다형적 참조 (물리 FK 없음)

다음 조합은 논리적 참조이며 Migration에 FK 제약을 만들지 않았다.

```text
files.owner_type + files.owner_id
notifications.resource_type + notifications.resource_id
async_operations.target_type + async_operations.target_id
audit_logs.target_type + audit_logs.target_id
```

## FK 삭제 정책

Migration 파일 상단 주석과 `V2__create_core_domain_schema.sql`의 각 `ON DELETE` 절이 최종 근거다. 원칙은 다음과 같다.

- 같은 Aggregate에 강하게 종속된 자식/연결 Row(`member_roles`, `refresh_tokens`, `member_job_preferences`, `job_ai_analyses`, `recommendations`, `notifications`)는 부모가 사라지면 의미를 잃으므로 `ON DELETE CASCADE`.
- 작성자/업로더/승인자처럼 이력의 주체가 아닌 Nullable 참조는 `ON DELETE SET NULL`로 레코드 자체를 보존한다(`files.uploader_member_id`, `members.profile_image_file_id`, `companies.logo_file_id`, `jobs.created_by_member_id`/`manager_member_id`, `inquiries.answered_by_member_id`, `async_operations.*`, `audit_logs.actor_member_id`).
- 지원/신청/제출/문의처럼 보존해야 하는 이력이면서 NOT NULL FK인 관계는 명시적 `ON DELETE`를 선언하지 않는다(PostgreSQL 기본값 `NO ACTION`). 참조가 남아있는 상위 Row(공고, 회원, 프로그램, 포트폴리오 요청)를 실수로 삭제해 이력이 함께 사라지는 것을 막는다.

## 확정하지 않고 남겨둔 정책 (DECISION_REQUIRED)

`program_applications`에 `UNIQUE(program_id, applicant_member_id)`를 추가할지는 "동일 회원 재신청을 하나의 Row 상태 전환으로 볼지, 여러 Row(이력)로 볼지"에 달려 있다. 이번 PR이 참고한 어떤 문서에도 이 정책이 확정되어 있지 않아 Migration은 ERD 원문 그대로 이 제약을 추가하지 않았다(여러 Row 허용, 데이터 손실 위험이 없는 더 보수적인 선택). 정책이 확정되면 이미 병합된 `V2` Migration을 수정하지 않고 새 버전(`V3` 등)으로 `ALTER TABLE ... ADD CONSTRAINT`를 추가한다.

## OAuth / 회원 정책 요약

- `members`에는 `oauth_provider`(`DG`/`GOOGLE`)와 `oauth_subject`(DG는 `/userinfo`의 최상위 `id`, `student.id`가 아님)만 저장하고 별도 `oauth_accounts` Table을 만들지 않는다. `UNIQUE(oauth_provider, oauth_subject)`.
- `members.name`은 NULL을 허용한다(DG/Google 흐름에 따라 최초 생성 시점에 이름이 없을 수 있음).
- `department`는 `SW_DEVELOPMENT`/`SMART_IOT`/`AI`만 사용하고 과거 `SOFTWARE`는 사용하지 않는다.
- 내부 역할(`role_type`)은 `STUDENT`/`TEACHER`/`DEVELOPER`이며 과거 `ADMIN`은 없다. `member_roles`가 다대다 관계를 관리한다.
- PKCE `state`/`code_verifier`와 DG/Google의 Access/Refresh Token은 PostgreSQL에 저장하지 않는다. `refresh_tokens.token_hash`는 GETI가 자체 발급한 Refresh Token의 해시만 담는다(원문 미저장, `UNIQUE`).

## Enum 목록

23개 Enum은 각 Domain Package의 `entity/type` Package에 있으며(예: `domain.member.entity.type.OAuthProvider`) `@Enumerated(EnumType.STRING)`으로 문자열 저장한다. DB 문자열과 Kotlin Enum 이름이 동일하다. 정확한 값은 각 Enum Kotlin 파일과 Migration의 `CHECK`/`VARCHAR` 정의를 참고한다.

## Test로 검증한 내용

[`CoreDomainSchemaIntegrationTest`](../../src/integrationTest/kotlin/team/inreok/getiserver/persistence/CoreDomainSchemaIntegrationTest.kt)가 PostgreSQL Testcontainers(`postgres:18.4-alpine`)로 다음을 검증한다.

```text
Flyway Migration이 정확히 19개의 비즈니스 Table을 생성하는지
ddl-auto=validate로 모든 Entity Mapping이 실제 Schema와 일치하는지
members.name NULL 허용, oauth_provider+oauth_subject UNIQUE
member_roles/member_job_preferences 복합키, job_ai_analyses 공유 PK
refresh_tokens.token_hash UNIQUE
jobs(source_name, external_job_id) Partial Unique Index(내부 공고는 여러 건 허용)
recommendations/job_applications/portfolio_submissions UNIQUE 제약
files.size_bytes, async_operations.progress_percent, job_applications.attempt_number CHECK 제약
member 삭제 시 member_roles/refresh_tokens CASCADE, files.uploader_member_id SET NULL
JSONB(members.majors) 저장/조회
다형적 참조 Column(files.owner_id, notifications.resource_id, async_operations.target_id, audit_logs.target_id)에 물리 FK가 없는지
19개 Table 전체(company/job/ai/recommendation/application/program/portfolio/notification/inquiry/collector/operation/audit 포함)의 최소 저장·조회 경로
```

```bash
./gradlew integrationTest --tests "*CoreDomainSchemaIntegrationTest*"   # Docker 필요
./gradlew test --tests "*ModularityTest"
```
