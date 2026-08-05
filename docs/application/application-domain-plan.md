# Application 도메인 구현 계획 (Epic)

## 0. 문서의 성격

이 문서는 2026-08-05 사용자가 채팅으로 전달한 "GETI-Server Application 도메인 전체 개발 요구사항"을 실행 계획으로 옮긴 것이다. GETI Notion 원문에 직접 접근할 수 있는 도구가 없어(AI Harness에 Notion 연동 없음), 사용자 확인에 따라 **이 요구사항 문서 자체를 최종 API 명세·기능명세 기준으로 채택**했다. 문서 안에서 "노션 확인 후 결정"으로 남겨둔 항목은 이 문서 §4 "결정 필요 사항"에 그대로 옮기고, 코드는 그 항목에 대해 임의의 세부 규격을 발명하지 않는다.

범위가 매우 커서 하나의 PR로 만들지 않는다. Epic Issue 아래 Phase별 하위 Issue로 나누고, 각 Phase는 `docs/job/job-core-plan.md` 선례처럼 필요 시 자체 상세 계획 문서를 갖는다. 이 문서는 전체 로드맵과 Phase 1(개인 신청 양식) 상세 설계를 담는다.

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
Phase 1  Form 및 Form Version                         ← 이 문서 §3, 이번 작업 범위
Phase 2  Application 초안·임시저장
Phase 3  제출 및 학생 Workflow (SUBMIT/REQUEST_EDIT/RESUBMIT/WITHDRAW)
Phase 4  교사 조회·검토 Workflow (목록/상세/ALLOW_EDIT/REQUEST_REVISION/APPROVE/REJECT)
Phase 5  상태 이력과 Snapshot 보강
Phase 6  File 연동 (지원서 첨부파일 업로드·교체·삭제·다운로드)
Phase 7  Notification 연동 지점
Phase 8  Job canApply 연동, 공고-양식 연결
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
6. Phase 2 이후로 넘어가는 원문의 기존 미해결 항목(임시저장 Endpoint 존재 여부, APPROVED→WITHDRAWN 허용 여부, 공개 신청자 상태 기준, 일괄 다운로드 파일 형식)은 해당 Phase 착수 시점에 다시 확인한다.

## 5. Phase 1 테스트 계획

- **Service Unit Test**: 생성(DRAFT/ACTIVE), 목록(소유자 필터), 상세(소유자 아님 거부), 수정(버전 증가/미증가, ARCHIVED 거부), Action(복제/활성화/보관/잘못된 전이), Field 검증(key/label/order/options/filePolicy 각 위반 사례)
- **Controller WebMvcTest**(`@Import(SecurityConfig::class)` + `@EnableWebSecurity`, Company 패턴 준수): 역할별 접근(TEACHER/DEVELOPER 허용, STUDENT 거부, 미인증 401), 성공 응답 JSON 형태, 오류 코드별 HTTP Status
- **OpenApiDocumentationTest**: 기존 Test가 자동으로 새 Endpoint를 검사하므로 별도 추가 없이 통과 여부만 확인
- **구조 Test**: `ModularityTest`, `PackageArchitectureTest` 재실행(새 Package 추가 없음 — 기존 `domain.application.*` 재사용이라 영향 적음)
- Docker가 필요한 Repository 통합 Test(Unique Constraint, JSONB 매핑)는 `src/integrationTest`(Phase 1 착수 시 Docker 사용 가능 여부에 따라 실행 여부 보고)
