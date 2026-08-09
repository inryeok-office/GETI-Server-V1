# Application 도메인 구현 계획 (Epic)

## 0. 문서의 성격

이 문서는 2026-08-05 사용자가 채팅으로 전달한 "GETI-Server Application 도메인 전체 개발 요구사항"을 실행 계획으로 옮긴 것이다. GETI Notion 원문에 직접 접근할 수 있는 도구가 없어(AI Harness에 Notion 연동 없음), 사용자 확인에 따라 **이 요구사항 문서 자체를 최종 API 명세·기능명세 기준으로 채택**했다. 문서 안에서 "노션 확인 후 결정"으로 남겨둔 항목은 이 문서 §4 "결정 필요 사항"에 그대로 옮기고, 코드는 그 항목에 대해 임의의 세부 규격을 발명하지 않는다.

범위가 매우 커서 하나의 PR로 만들지 않는다. Epic Issue 아래 Phase별 하위 Issue로 나누고, 각 Phase는 `docs/job/job-core-plan.md` 선례처럼 필요 시 자체 상세 계획 문서를 갖는다. 이 문서는 전체 로드맵과 Phase 1(개인 신청 양식)·Phase 2(공고-양식 연결·지원가능여부·초안·임시저장) 상세 설계를 담는다.

**Phase 1 완료(PR #77, 2026-08-05 Merge) 후 로드맵 수정**: 원래 §2는 "공고-양식 연결"을 Phase 8로 미뤘으나, Phase 2(지원서 초안 생성)를 시작하려면 "공고에 어떤 Form이 연결되어 있는가"를 먼저 알아야 해 순서가 맞지 않았다. 공고-양식 연결(요구사항 6절)과 그 연결을 사용하는 지원 가능 여부 판단(7절)은 **Phase 2로 앞당기고**, Phase 8은 "이미 Phase 2가 계산하는 지원 가능 여부를 Job 상세·검색 결과에 노출하는 공개 Query Port"로 범위를 좁힌다(요구사항 7절 마지막 문단이 이 방향을 명시).

## 1. 기존 코드 대비 확인 사실

- `domain/application/entity/JobApplication.kt` + `JobApplicationStatus`(Entity/Repository만, Service/Controller 없음)가 `V2__create_core_domain_schema.sql`에 이미 병합되어 있다.
- `domain/file`, `domain/notification`도 Entity/Repository만 있고 Service/Controller가 없다(요구사항 19·20절이 예상한 상태와 일치).
- `Form`/`FormVersion` 관련 테이블·Entity는 전혀 없다.
- Job PR(#60)은 `formId` 필드를 명시적으로 범위에서 제외했고(`docs/job/job-core-plan.md` §1), 이번 Application 작업이 그 후속이다.

### 1.1 기존 코드와 요구사항의 불일치 (사용자 확인 완료)

| 항목 | 기존 코드 | 이번 요구사항 | 결정 |
| --- | --- | --- | --- |
| 기업 전달 기능 | `JobApplicationStatus.FORWARDED`, `job_applications.forwarded_at` 존재 | 기업 전달·전달완료 상태 구현 안 함(27절) | **손대지 않고 그대로 둠.** 새 Application 상태 전이 로직·API에서는 `FORWARDED`를 생성·참조하지 않는다. 기존 값·컬럼 삭제는 이번 범위 밖(별도 Migration 필요, 사용 여부 재확인 후 처리) |
| 공고-양식 연결 구조 | `jobs.application_form_schema JSONB`(공고에 양식 스키마 직접 내장, V2) | 교사 개인 소유 재사용 Form을 별도 `forms`/`form_versions` 테이블로 관리(5절) | **요구사항대로 별도 `forms`/`form_versions` 테이블 신설.** `jobs.application_form_schema` 컬럼은 건드리지 않고 이번 기능에서 사용하지 않는다(죽은 컬럼으로 남음 — 후속 정리는 별도 Issue 후보). 공고-양식 연결(6절, Phase 8)은 Job 소유 컬럼을 새로 추가하지 않고 Application 쪽에 매핑을 둔다(§3.8 참고) |
| `JobApplication` 구조 | `answers` 단일 JSONB, Form 연결 컬럼 없음, 지원자 스냅샷 컬럼 없음, 상태 이력 테이블 없음 | Form 연결, 답변/지원자 스냅샷, 상태 이력 필요(21절) | Phase 3(제출)에서 **기존 Entity를 확장**한다(폐기하지 않음). 상세 설계는 해당 Phase 계획에서 다룬다 |

## 2. 전체 로드맵 (Epic 하위 Phase)

```
Phase 1  Form 및 Form Version                                    ← 완료(PR #77)
Phase 2  공고-양식 연결, 학생 지원가능여부, 초안 생성·임시저장      ← 이 문서 §6, 이번 작업 범위
Phase 3  제출 및 학생 Workflow (SUBMIT/REQUEST_EDIT/RESUBMIT/WITHDRAW)
Phase 4  교사 조회·검토 Workflow (목록/상세/ALLOW_EDIT/REQUEST_REVISION/APPROVE/REJECT)
Phase 5  상태 이력과 Snapshot 보강
Phase 6  File 연동 (지원서 첨부파일 업로드·교체·삭제·다운로드)
Phase 7  Notification 연동 지점
Phase 8  Job/Search에 지원가능여부(canApply) 노출하는 공개 Query Port
Phase 9  공개 신청자 목록
Phase 10 지원자 자료 일괄 다운로드
Phase 11 전체 회귀 검증
```

각 Phase는 완료 후 다음 Phase 시작 전에 `develop` 기준 최신화와 회귀 Test를 거친다. Phase 2 이후의 상세 설계(엔드포인트별 요청/응답, 오류 코드, 상태 전이, Snapshot 전략)는 요구사항 원문 6~20절에 이미 기술되어 있으므로, 해당 Phase 착수 시점에 최신 코드 상태를 다시 확인해 세부 계획 문서를 추가한다.

## 3. Phase 1 상세 설계: 개인 신청 양식 (Form)

### 3.1 범위

요구사항 5절 전체(5.1~5.7). 6절(공고-양식 연결)과 Phase 8은 이번 범위 밖 — Form은 이 Phase에서는 아직 어떤 공고에도 연결되지 않는 독립 개인 템플릿으로만 존재한다.

### 3.2 스키마 설계 (신규 Migration, `V11__create_form_tables.sql`)

```sql
-- forms: 교사/개발자 개인 소유 재사용 신청 양식 템플릿
CREATE TABLE forms (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    owner_member_id   BIGINT NOT NULL REFERENCES members(id),
    name              VARCHAR(255) NOT NULL,
    description       TEXT,
    form_type         VARCHAR(20) NOT NULL,   -- JOB | PROGRAM
    status            VARCHAR(20) NOT NULL DEFAULT 'DRAFT', -- DRAFT | ACTIVE | ARCHIVED
    current_version   INTEGER NOT NULL DEFAULT 1,
    created_at        TIMESTAMP NOT NULL DEFAULT now(),
    updated_at        TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_forms_owner_member_id ON forms (owner_member_id);

-- form_versions: 버전별 필드 구조 스냅샷(불변, Update/삭제 없음)
CREATE TABLE form_versions (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    form_id      BIGINT NOT NULL REFERENCES forms(id),
    version      INTEGER NOT NULL,
    schema_data  JSONB NOT NULL,
    created_at   TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uk_form_versions_form_version UNIQUE (form_id, version)
);
```

`members` 테이블 참조는 기존 Job/Company 패턴과 동일하게 평범한 `BIGINT` FK Column으로 두고 JPA 연관관계(`@ManyToOne`)는 만들지 않는다(`docs/architecture/erd.md` 원칙, Modulith 경계 유지).

`schema_data`는 필드 배열을 JSON 그대로 저장한다(기존 `jobs.target_condition`, `job_applications.answers`와 동일한 관례). 개별 필드를 관계형 Column으로 쪼개지 않는다 — 필드 구성이 Form마다 가변적이고, 조회 시점에 구조화된 검증/렌더링은 Application 계층(Kotlin `FormFieldSchema` 역직렬화)이 담당한다.

### 3.3 Entity/Enum (`domain/application/entity`, `domain/application/entity/type`)

- `Form`: `id, ownerMemberId, name, description?, formType(FormType), status(FormStatus), currentVersion, createdAt, updatedAt`
- `FormVersion`: `id, formId, version, schemaData(String, JSONB), createdAt`
- `FormType { JOB, PROGRAM }`, `FormStatus { DRAFT, ACTIVE, ARCHIVED }`, `FormFieldType { TEXT, TEXTAREA, SINGLE_SELECT, MULTI_SELECT, FILE }`, `FormAction { DUPLICATE, ACTIVATE, ARCHIVE }`(5.6절의 "복제/활성화/보관"에 대응하는 API 표기. 원문이 한글 명사만 제시해 영문 값은 이번 PR에서 새로 정한다)

`schema_data` 안의 필드 구조(Kotlin 표현, JSON 직렬화 대상):

```kotlin
data class FormFieldSchema(
    val key: String,
    val type: FormFieldType,
    val label: String,
    val description: String?,
    val required: Boolean,
    val order: Int,          // 요청 배열의 index를 그대로 사용 (3.6 참고)
    val options: List<String>?,
    val filePolicy: JsonNode?, // 내부 구조를 강제하지 않음 (3.7, 결정 필요 사항)
)
```

### 3.4 API 계약

공통: `TEACHER`, `DEVELOPER`만 접근(`SecurityConfig`에 `hasAnyRole("TEACHER","DEVELOPER")` 추가), 모든 조회·수정·Action은 `ownerMemberId == 현재 로그인 memberId` 검증.

#### `POST /api/v1/me/forms` → 201
요청 `CreateFormRequest(name, formType, description?, fields: List<FormFieldRequest>, status: DRAFT|ACTIVE = DRAFT)`.
처리: 필드 검증(3.6) → `Form(currentVersion=1)` 저장 → `FormVersion(version=1, schemaData=검증된 fields)` 저장.
응답 `FormCreateResponse(formId, version=1, status, createdAt)`.
오류: `INVALID_FORM_FIELD`(400, 필드 검증 실패), 생성 시 `status`가 `ARCHIVED`면 `INVALID_FORM_FIELD`(생성 시점에 보관 상태로 만들 수 없음).

#### `GET /api/v1/me/forms` → 200
Query `formType?, status?, page=0, size=20(최대 100)`. `ownerMemberId = 현재 사용자`로만 필터링.
응답 `FormListResponse(content: List<FormSummaryResponse{formId,name,formType,status,version,updatedAt}>, page, size, totalElements, totalPages, first, last)` — Company/Job과 동일한 평평한 Page 계약(`global.web.PageResponse`는 프로젝트에서 미채택 상태 유지, Job PR #60 결정 계승).

#### `GET /api/v1/me/forms/{formId}` → 200
응답 `FormDetailResponse(formId, name, formType, status, schemaData: List<FormFieldResponse{fieldId,type,title,description,required,order,options,filePolicy}>, createdAt, updatedAt)`. `fieldId`=`key`, `title`=`label`을 그대로 매핑한다(요구사항 5.2 요청과 5.4 응답의 명칭 차이를 동일 개념으로 취급, §4 참고).
오류: `FORM_NOT_FOUND`(404, 존재하지 않음), `NOT_FORM_OWNER`(403, 존재하지만 소유자 아님).

#### `PATCH /api/v1/me/forms/{formId}` → 200
요청 `UpdateFormRequest(name?, description?, fields: List<FormFieldRequest>?, status?)`.
정책:
- 소유자만, `FORM_ARCHIVED`인 Form은 어떤 필드든 수정 거부(409)
- `fields`가 전달된 경우에만 새 `FormVersion(version=currentVersion+1)` 생성 + `Form.currentVersion` 증가 (name/description/status만 바뀌는 경우는 새 버전을 만들지 않음 — 3.6 설계 결정)
- `name/description/status`는 전달된 값만 부분 수정(Company `PATCH` 관례와 동일)
응답 `FormUpdateResponse(formId, version, affectedJobIds: List<Long> = emptyList(), notificationCreated=false, updatedAt)`. `affectedJobIds`는 공고-양식 연결이 Phase 8에서 구현되므로 이번 Phase에서는 항상 빈 배열이다(허위 데이터 금지 원칙, 20절과 동일 취지). `notificationCreated`는 Notification 미연동이라 항상 `false`(20절 요구사항 그대로).
오류: `FORM_NOT_OWNED`(403), `FORM_NOT_FOUND`(404), `FORM_ARCHIVED`(409), `INVALID_FORM_FIELD`(400, `fields` 제공 시 검증 실패).

#### `POST /api/v1/me/forms/{formId}/actions` → 200
요청 `FormActionRequest(action: FormAction, newName: String?)`.
- `DUPLICATE`: 새 `Form(ownerMemberId=호출자, name=newName ?: "{원본 이름} 복사본", formType=원본과 동일, status=DRAFT, currentVersion=1)` + 최신 `FormVersion`의 `schemaData`를 복사한 `FormVersion(version=1)` 생성. 원본 지원서·답변은 복제하지 않는다(Form만 복제, 5.6절).
- `ACTIVATE`: `DRAFT|ARCHIVED → ACTIVE`만 허용
- `ARCHIVE`: `DRAFT|ACTIVE → ARCHIVED`만 허용
- 그 외 전이(동일 상태 포함)는 `FORM_ACTION_INVALID`(400)
응답 `FormActionResponse(formId, name, formType, status, version, updatedAt)` — `DUPLICATE`는 새로 생성된 Form 기준.
오류: `FORM_NOT_OWNED`(403), `FORM_NOT_FOUND`(404), `FORM_ACTION_INVALID`(400).

### 3.5 ErrorCode (`domain/application/exception/ApplicationErrorCode.kt`, 신규)

```
FORM_NOT_FOUND        404
NOT_FORM_OWNER        403
FORM_NOT_OWNED        403
FORM_ARCHIVED         409
FORM_ACTION_INVALID   400
INVALID_FORM_FIELD    400
```

`NOT_FORM_OWNER`(상세 조회)와 `FORM_NOT_OWNED`(수정·Action)를 요구사항 24절이 실제로 구분해 나열하므로 그대로 두 개로 구현한다(§4에 설계 노트로 기록). 두 코드 모두 대상 Form이 존재는 하되 소유자가 아닌 경우에 사용하고, 존재하지 않는 Form은 항상 `FORM_NOT_FOUND`다.

### 3.6 Field 검증 규칙 구현 (요구사항 5.7, 정의 시점만 — 제출값 검증은 Phase 3)

- `key` trim 후 공백 금지, 전체 필드 간 중복 금지
- `label` trim 후 공백 금지
- `order`는 클라이언트가 보내지 않는다 — **요청 배열의 index를 그대로 order로 사용한다.** (요구사항 5.2 요청 JSON에는 `order` 필드가 없는데 5.7은 "order 중복 금지"를 요구해 원문 내부 모순처럼 보인다. 배열 순서를 order로 삼으면 중복이 애초에 불가능해 이 요구를 항상 만족한다 — 설계 결정, §4 기록)
- `type`이 `SINGLE_SELECT`/`MULTI_SELECT`면 `options` 1개 이상 + 중복 금지
- `type`이 `TEXT`/`TEXTAREA`면 `options`는 `null`이거나 빈 배열이어야 함(값이 있으면 거부)
- `type`이 `FILE`이면 `filePolicy` 필수(`null` 거부), 그 외 타입은 `filePolicy` 금지
- 위반 시 모두 `InvalidFormFieldException`(→ `INVALID_FORM_FIELD`, 400), Message에 위반 사유 포함(요청 값 자체는 되돌려주지 않음)

### 3.7 filePolicy 처리

`filePolicy`의 내부 속성(최대 용량, 허용 확장자, 개수 제한 등)이 이번 요구사항에도 노션에도 확정되어 있지 않다. **임의 구조를 발명하지 않고 원문 JSON(`JsonNode`)을 그대로 보관·왕복**시킨다. `FILE` 타입일 때 존재 여부만 검증하고 내부 필드는 검증하지 않는다. 실제 제출 시 파일 정책 적용(Phase 3/6, `FILE_POLICY_VIOLATED`)은 이 구조가 확정된 뒤 구현한다.

### 3.8 Job 연동 관련 참고 (이번 Phase에서 구현하지 않음)

요구사항 6절 "공고와 양식 연결"은 Phase 8이다. 이번 Phase는 Form을 어떤 공고와도 연결하지 않으며, `jobs` 테이블에 새 Column을 추가하지 않는다. Phase 8에서 매핑을 어느 domain에 둘지(Application 쪽에 `job_id → form_id` 매핑 테이블을 두는 안 vs `jobs`에 `active_form_id` Column을 추가하는 안)는 Job 도메인 소유권 문제라 그 Phase 착수 시점에 별도로 검토한다.

## 4. 결정 필요 사항 (DECISION_REQUIRED, 사용자 확인 필요)

이번 Phase 1 구현은 아래 항목에 대해 보수적인 기본값으로 진행했다. 실제 GETI Notion 원문을 확인할 수 있게 되면 재검토가 필요하다.

1. **filePolicy 내부 스키마** — §3.7. 구조를 정하지 않고 원문 JSON을 그대로 보관.
2. **`order` 필드 부재** — 요구사항 5.2 요청 JSON에 `order`가 없는데 5.7 검증 규칙은 "order 중복 금지"를 요구. 배열 index를 order로 채택(§3.6).
3. **`fieldId`/`title`(5.4 응답) vs `key`/`label`(5.2 요청) 명칭 차이** — 같은 개념으로 간주해 매핑(§3.4 상세 조회).
4. **PATCH가 항상 버전을 증가시키는지** — 요구사항 5.5는 "수정 시 새 Form Version 생성"이라고만 쓰여 있어 `name`/`status`만 바뀌어도 버전이 오르는지 불명확. 이번 구현은 `fields`가 실제로 전달될 때만 버전을 올린다(§3.4 PATCH).
5. **FormAction의 영문 값(`DUPLICATE`/`ACTIVATE`/`ARCHIVE`)** — 원문은 "복제/활성화/보관" 한글 명사만 제시. 이번 PR에서 새로 이름 붙임.
6. **(Phase 2) 학번(studentNumber) 부재** — `Member` 실제 스키마에 학번 Column이 없다. 요구사항 8절 자동입력 항목에서 명시적으로 제외했다(사용자 확인 완료, §6.1/§6.3). 실제 학번이 필요해지면 Member 도메인에 별도 Migration을 먼저 추가해야 한다.
7. **(Phase 2) 공고-양식 연결 API 계약 신설** — 요구사항 6절은 검증 규칙만 나열하고 Endpoint를 정의하지 않아 `POST /api/v1/admin/jobs/{jobId}/application-form`을 이번 PR에서 새로 설계했다(§6.4).
8. **(Phase 2) 임시저장 Endpoint** — 요구사항 9절도 별도 Endpoint Path를 주지 않아, 초안 생성과 같은 Resource를 `PATCH /api/v1/job-applications/{applicationId}`로 재사용하도록 설계했다(§6.6).
9. **(Phase 2) Form 미연결 공고의 eligibilityReason** — 요구사항 4절 Enum에 "양식 미연결"에 대응하는 값이 없어 `JOB_NOT_PUBLISHED`로 재사용했다(§6.5 8번).
10. Phase 3 이후로 넘어가는 원문의 기존 미해결 항목(APPROVED→WITHDRAWN 허용 여부, 공개 신청자 상태 기준, 일괄 다운로드 파일 형식)은 해당 Phase 착수 시점에 다시 확인한다.

## 5. Phase 1 테스트 계획

- **Service Unit Test**: 생성(DRAFT/ACTIVE), 목록(소유자 필터), 상세(소유자 아님 거부), 수정(버전 증가/미증가, ARCHIVED 거부), Action(복제/활성화/보관/잘못된 전이), Field 검증(key/label/order/options/filePolicy 각 위반 사례)
- **Controller WebMvcTest**(`@Import(SecurityConfig::class)` + `@EnableWebSecurity`, Company 패턴 준수): 역할별 접근(TEACHER/DEVELOPER 허용, STUDENT 거부, 미인증 401), 성공 응답 JSON 형태, 오류 코드별 HTTP Status
- **OpenApiDocumentationTest**: 기존 Test가 자동으로 새 Endpoint를 검사하므로 별도 추가 없이 통과 여부만 확인
- **구조 Test**: `ModularityTest`, `PackageArchitectureTest` 재실행(새 Package 추가 없음 — 기존 `domain.application.*` 재사용이라 영향 적음)
- Docker가 필요한 Repository 통합 Test(Unique Constraint, JSONB 매핑)는 `src/integrationTest`(Phase 1 착수 시 Docker 사용 가능 여부에 따라 실행 여부 보고)

## 6. Phase 2 상세 설계: 공고-양식 연결, 지원가능여부, 초안·임시저장

### 6.1 착수 전 확인 사실

- `jobs.application_form_schema JSONB`는 Job Service/Controller 어디서도 쓰이지 않는 죽은 Column이다(Job PR #60이 `formId` 자체를 범위 제외).
- `jobs`에 Form 연결 Column이 전혀 없고, `jobs.target_grade`는 배열이 아니라 단일 `Int?`다.
- Job이 공개한 계약은 Search 전용 `JobIndexQueryPort`(PUBLISHED/CLOSED가 아니면 null) 하나뿐이라 Application 용도로 재사용하기엔 목적이 다르다 — 새 Named Interface가 필요하다.
- `Member`에 **학번(studentNumber) Column이 없다**. 요구사항 8절이 자동입력 항목으로 요구하지만 실제 스키마에 없다 — **사용자 확인에 따라 이번 Phase는 studentNumber를 자동입력·스냅샷에서 제외**한다(DECISION_REQUIRED, §4에 추가 기록).
- `MemberSelectionQueryService`(전공/기술스택 이름 조회)는 있지만 `@NamedInterface`가 아니다. 이를 직접 공개하는 대신, Member 모듈 안에 새 `MemberApplicantSnapshotQueryPort`를 만들고 그 구현체가 내부적으로 기존 Service를 호출한다(같은 Module이라 경계 문제 없음, 기존 Service의 가시성은 바꾸지 않음).
- `JobApplication`(Entity)에는 Form 연결 Column, 지원자 스냅샷 Column이 없다. `answers`는 이미 JSONB라 요구사항의 답변 배열 구조를 그대로 담을 수 있다.
- **공고-양식 연결(6절)에 대한 REST Endpoint 계약이 요구사항 원문에 없다**(검증 규칙만 나열되어 있고 Path/Method/JSON 예시가 없음). 이번 Phase에서 새로 설계해야 한다(§6.4, DECISION_REQUIRED).
- **임시저장(9절) 전용 Endpoint**도 원문에 Path가 없다. 이번 Phase에서 초안 생성 Endpoint의 후속 수정 Endpoint로 설계한다(§6.6, DECISION_REQUIRED, §4의 기존 항목과 동일 맥락).

### 6.2 공고-양식 연결을 저장하는 위치 (사용자 확인 완료)

`jobs` 테이블에 Column을 추가하지 않는다. Application 도메인에 새 매핑 테이블을 둔다 — Job Migration/Entity를 이번 PR에서 건드리지 않고, 요구사항 7절이 암시하는 방향(Application이 지원가능여부를 계산해 Job/Search가 그 결과를 소비, Phase 8)과도 맞는다.

```sql
-- job_application_forms: 공고 하나당 활성 양식 하나(1:1). 재연결은 UPSERT.
CREATE TABLE job_application_forms (
    job_id BIGINT PRIMARY KEY,
    form_id BIGINT NOT NULL REFERENCES forms(id),
    linked_by_member_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
```

`job_id`는 Job 소유이므로 물리 FK를 걸지 않는다(다른 Domain FK와 동일한 관례, `docs/architecture/erd.md`). `form_id`는 같은 Application 모듈 안이라 물리 FK를 건다.

### 6.3 새 Named Interface (Job, Member)

**`domain.job.query.JobApplicationSnapshotQueryPort`(신규)**
```kotlin
@NamedInterface
interface JobApplicationSnapshotQueryPort {
    /** 존재하지 않거나 삭제됐으면 null. 상태(DRAFT 포함)는 그대로 반환 — 지원가능여부 판단은
     *  Application이 이 값을 보고 직접 수행한다(JobIndexQueryPort와 달리 여기서 필터링하지 않음). */
    fun findById(jobId: Long): JobApplicationSnapshot?
}

@NamedInterface
data class JobApplicationSnapshot(
    val jobId: Long,
    val title: String,
    val companyId: Long,
    val postingType: String,           // PostingType.name
    val applicationMethod: String,     // ApplicationMethod.name
    val status: String,                // JobStatus.name
    val targetGrade: Int?,
    val recruitmentStartedAt: LocalDateTime?,
    val recruitmentEndedAt: LocalDateTime?,
    val createdByMemberId: Long?,
    val managerMemberId: Long?,
)
```
공고-양식 연결 권한 검증(등록자·담당 교사)과 지원가능여부 판단(§6.5) 양쪽에 이 하나로 충분하다.

**`domain.member.query.MemberApplicantSnapshotQueryPort`(신규)**
```kotlin
@NamedInterface
interface MemberApplicantSnapshotQueryPort {
    /** 존재하지 않으면 null. */
    fun findById(memberId: Long): MemberApplicantSnapshot?
}

@NamedInterface
data class MemberApplicantSnapshot(
    val memberId: Long,
    val name: String?,
    val email: String,
    val phone: String?,
    val academicStatus: String?,   // AcademicStatus.name
    val grade: Int?,
    val cohort: Int?,
    val department: String?,       // DepartmentType.name
    val majors: List<String>,
    val techStacks: List<String>,
    val desiredJob: String?,       // Member.desiredPositions(JSON 배열)의 첫 값 — MemberServiceImpl과 동일한 관례
)
```
studentNumber는 §6.1 결정에 따라 포함하지 않는다.

### 6.4 공고-양식 연결 API (신규 설계, 원문에 없어 이번 PR이 확정)

```
POST /api/v1/admin/jobs/{jobId}/application-form   → 200
```
권한: 해당 공고의 등록자(`createdByMemberId`) 또는 담당 교사(`managerMemberId`), 개발자.
요청 `{ formId: Long }`. 검증: 공고 존재(`JOB_NOT_FOUND`), INTERNAL 지원방식(`JOB_APPLICATION_METHOD_NOT_INTERNAL`), 공고 상태가 DELETED가 아님, 양식 존재(`FORM_NOT_FOUND`), 양식 소유자 == 요청자(`FORM_NOT_OWNED`), `formType == JOB`(`INVALID_FORM_FIELD`), 양식 상태 `ACTIVE`(`FORM_NOT_ACTIVE`), 요청자가 공고 등록자·담당교사·개발자 중 하나(`JOB_MANAGE_FORBIDDEN`).
응답 `JobApplicationFormLinkResponse(jobId, formId, formVersion, updatedAt)`.

`DELETE /api/v1/admin/jobs/{jobId}/application-form`는 이번 Phase 범위에 넣지 않는다(요구사항에 언급 없음, 실제 필요성이 확인되면 후속).

### 6.5 학생 지원 가능 여부 (요구사항 7절)

```
GET /api/v1/jobs/{jobId}/application-eligibility   → 200
```
권한: 인증된 사용자(STUDENT 기준으로 판단하되, TEACHER/DEVELOPER가 호출하면 `NOT_ENROLLED` 계열로 자연히 거부되도록 함 — 별도 Role 강제는 하지 않는다. 화면은 학생만 호출).

판단 순서와 `JobApplicationEligibilityReason`(요구사항 4절 Enum 그대로) 매핑:
1. Job 없음/삭제 → `JOB_NOT_PUBLISHED`
2. `status != PUBLISHED` → `JOB_NOT_PUBLISHED`
3. `applicationMethod != INTERNAL` → `NOT_INTERNAL`
4. 요청자 `academicStatus != ENROLLED` → `NOT_ENROLLED`
5. `targetGrade != null && targetGrade != member.grade` → `NOT_TARGET_GRADE`
6. `recruitmentStartedAt != null && now < recruitmentStartedAt` → `BEFORE_START`
7. `recruitmentEndedAt != null && now > recruitmentEndedAt` → `AFTER_END`
8. `job_application_forms`에 연결된 Form이 없거나 그 Form이 `ACTIVE`가 아님 → `JOB_NOT_PUBLISHED`로 취급한다(요구사항이 별도 Reason을 정의하지 않아, "공고 자체가 아직 지원받을 준비가 안 됨"과 같은 의미로 기존 값을 재사용 — DECISION_REQUIRED로 기록)
9. 이 학생의 활성(`DRAFT|SUBMITTED|EDIT_REQUESTED|EDIT_ALLOWED|REVISION_REQUESTED|APPROVED`) `job_applications` Row가 이미 있음 → `ALREADY_APPLIED`
10. 위 전부 통과 → `AVAILABLE`

응답 `JobEligibilityResponse(canApply: Boolean, eligibilityReason, eligibilityMessage, availableActions: List<String>)`. `availableActions`는 `canApply`면 `["CREATE_DRAFT"]`, 아니면 빈 배열.

### 6.6 지원서 초안 생성과 임시저장

`POST /api/v1/jobs/{jobId}/applications`(요구사항 8절)는 §6.5의 판정을 서버가 다시 수행하고(클라이언트가 이미 조회했더라도 신뢰하지 않음), `AVAILABLE`이 아니면 `JOB_NOT_APPLICABLE`로 거부한다. 통과하면:
- `JobApplication(jobId, applicantMemberId, attemptNumber=이전 최대값+1, status=DRAFT, formId=연결된 Form, formVersion=그 Form의 currentVersion, contactEmail/contactPhone/스냅샷 Column=prefillProfileFields=true일 때 §6.3 Port 결과로 채움, answers="[]")` 저장.
- `attemptNumber`는 기존 `uk_job_applications_job_applicant_attempt`(V2) Unique 제약을 그대로 활용한다 — 취소 후 재지원은 새 `attemptNumber`로 새 Row가 된다(요구사항 22절과 자동으로 맞음, 별도 이력 테이블 불필요).

임시저장은 **별도 신규 Endpoint 없이 초안 생성과 같은 Endpoint를 `PATCH`로 재사용**한다(DECISION_REQUIRED, 노션 원문 확인 전 임시 결정):
```
PATCH /api/v1/job-applications/{applicationId}   → 200
```
요청 `{ contactPhone?, answers?: List<AnswerRequest>, privacyConsent? }`. 소유자 본인만, 상태가 `DRAFT`이거나 §6.7에서 정의할 "수정 허용" 상태일 때만 허용(Phase 2는 `DRAFT`만 해당 — 나머지 상태는 Phase 3). 필수값 누락 상태로도 저장 가능(요구사항 9절), 제출 일시는 만들지 않는다.

### 6.7 JobApplication Entity 확장 (기존 Entity 유지, Column 추가)

```sql
-- V12__extend_job_applications_for_forms.sql
ALTER TABLE job_applications ADD COLUMN form_id BIGINT REFERENCES forms(id);
ALTER TABLE job_applications ADD COLUMN form_version INTEGER;
ALTER TABLE job_applications ADD COLUMN privacy_consent BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE job_applications ADD COLUMN applicant_name VARCHAR(100);
ALTER TABLE job_applications ADD COLUMN applicant_cohort INTEGER;
ALTER TABLE job_applications ADD COLUMN applicant_department VARCHAR(30);
ALTER TABLE job_applications ADD COLUMN applicant_majors JSONB;
ALTER TABLE job_applications ADD COLUMN applicant_desired_job VARCHAR(255);
ALTER TABLE job_applications ADD COLUMN applicant_tech_stacks JSONB;
```
`contactEmail`/`contactPhone`은 기존 Column을 그대로 연락처 스냅샷으로 쓴다. `answers`(기존 JSONB)에 담는 Kotlin 표현:
```kotlin
data class ApplicationAnswer(val fieldId: String, val value: JsonNode?, val fileIds: List<Long>?)
```
`fileIds`는 Phase 6(File 연동) 전까지 항상 비워 두되 구조는 미리 만들어 둔다(값을 검증하지 않고 그대로 왕복).

### 6.8 ErrorCode 추가 (`ApplicationErrorCode`)

```
JOB_NOT_APPLICABLE            400
ACTIVE_APPLICATION_EXISTS     409
APPLICATION_NOT_FOUND         404
APPLICATION_ACCESS_FORBIDDEN  403
JOB_APPLICATION_METHOD_NOT_INTERNAL   400   (§6.4 연결 검증)
FORM_NOT_ACTIVE                       400   (§6.4 연결 검증)
JOB_MANAGE_FORBIDDEN                  403   (§6.4 연결 권한 — Job 도메인 JOB_MANAGE_FORBIDDEN과 별개로 Application이 자체 판정)
```
`JOB_NOT_FOUND`/`FORM_NOT_FOUND`/`FORM_NOT_OWNED`/`INVALID_FORM_FIELD`는 기존 Job/Application ErrorCode를 재사용한다.

### 6.9 테스트 계획

Phase 1과 동일한 층위(Service Unit/Controller WebMvc/OpenApiDocumentationTest/Modularity·PackageArchitectureTest)에 더해:
- `JobApplicationSnapshotQueryPortImpl`, `MemberApplicantSnapshotQueryPortImpl` 자체 Unit Test(각 Domain 안에서)
- 지원가능여부 판정 10가지 분기(§6.5) 각각의 Unit Test
- 초안 생성: 정상 생성, 중복 활성 지원서 거부, 취소 후 재지원(새 attemptNumber), prefillProfileFields 스냅샷 확인
- 임시저장: 소유자만 가능, DRAFT 상태만 가능, 필수값 누락 상태 저장 허용
- 연결 API: 소유자 아닌 양식 연결 거부, ACTIVE 아닌 양식 연결 거부, 담당자 아닌 교사 연결 거부
- `src/integrationTest`: `job_application_forms` FK/PK 제약, `job_applications` 신규 Column 매핑
