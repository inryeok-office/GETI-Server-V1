다음 작업은 GETI-Server의 Job 도메인 개발입니다.

Job은 취업공고의 작성, 임시저장, 게시, 수정, 마감, 삭제, 목록·상세 조회, 조회수, 출처, 북마크를 담당하는 핵심 도메인입니다.

Collector, Search, AI Analysis, Recommendation, Application, File 도메인이 이후 Job을 기준으로 연결되기 때문에, 다른 도메인의 책임을 Job 안에 미리 구현하지 말고 공개 인터페이스와 확장 지점만 명확하게 만들어 주세요.

## 1. 작업 시작 전 확인

먼저 최신 `develop`을 기준으로 작업해 주세요.

```bash
git fetch origin
git switch develop
git pull origin develop
git status --short
```

작업 트리가 깨끗하지 않으면 기존 변경을 임의로 삭제하거나 덮어쓰지 말고 먼저 공유해 주세요.

다음 항목을 확인한 뒤 설계를 시작해 주세요.

* Company PR #57이 실제 `develop`에 반영됐는지
* 최신 Flyway Migration 번호
* 기존 Job 관련 Entity, Repository, Migration 존재 여부
* `AGENTS.md`
* `.claude/rules/`
* `docs/architecture/erd.md`
* `docs/development/web-api.md`
* `docs/ai/openapi-documentation.md`
* `docs/audit/notion-repository-sync.md`
* Auth, Member, Company의 실제 구현 패턴
* `ModularityTest`
* `PackageArchitectureTest`
* `OpenApiDocumentationTest`

기존 Job Entity나 Repository가 이미 있으면 다시 만들지 말고 현재 구조를 먼저 분석해 주세요.

병합된 Migration은 수정하지 않습니다. Schema 변경이 필요하면 최신 번호 다음의 신규 Migration을 추가해야 합니다.

## 2. Job 도메인의 책임

Job 도메인은 다음 데이터를 직접 소유합니다.

* 공고 ID
* 기업 식별자
* 공고 출처 식별자
* 제목
* Markdown 본문
* 공고 유형
* 지원 방식
* 공고 상태
* 모집 시작·종료 시각
* 지원 대상 학년
* 외부 지원 URL
* 조회수
* 작성자 또는 담당자 식별자
* 생성·수정·삭제 시각
* 북마크 관계
* 공고 출처 정보

다음 기능은 Job 도메인이 직접 구현합니다.

* 공고 임시저장
* 공고 내용 수정
* 공고 게시
* 공고 마감
* 공고 Soft Delete
* 공개 공고 목록
* 공고 상세 조회
* 상세 조회 시 조회수 증가
* 공고 출처 목록
* 북마크 등록·해제
* 내 북마크 목록
* 공고 필터와 정렬
* 공고 공개 여부 판단

## 3. 이번 작업에서 분리할 범위

Job 도메인이 다른 도메인의 기능을 직접 구현하면 안 됩니다.

### 이번 Job 작업에서 구현하지 않을 항목

* 외부 채용공고 수집
* 사람인·고용24 API 연동
* Elasticsearch 색인과 검색
* LLM 호출
* AI 요약 생성
* AI 재분석 처리
* 추천 점수 생성
* 지원서 작성·제출
* 내부 지원 상태 관리
* 파일 업로드와 다운로드 URL 발급
* Discord 공고 전송
* 자동 마감 Scheduler
* 알림 생성

### 후속 도메인 책임

* Collector: 외부 공고 수집 및 Job 등록 요청
* Search: Elasticsearch 색인과 고급 검색
* AI Analysis: 최초 분석과 재분석
* Recommendation: 사용자별 공고 추천
* Application/Form: 내부 지원과 신청서
* File: 첨부파일 검증·저장·조회
* Notification: 공고 게시·마감·삭제 알림
* Scheduler: 자동 마감과 주기 작업

Job 도메인은 후속 도메인이 사용할 수 있는 Service Interface, Query Interface 또는 Event만 제공합니다.

다른 도메인의 Entity나 Repository를 직접 참조하지 마세요.

## 4. 권장 개발 단위

Job 전체는 Endpoint가 많고 다른 도메인과의 연결 지점도 많기 때문에 하나의 거대한 PR로 구현하지 않는 것을 권장합니다.

### 1차: Job Core

다음 범위를 먼저 구현합니다.

* 공고 출처
* 공고 등록·임시저장
* 공고 수정
* 공고 상태 변경
* 공고 목록
* 공고 상세
* 조회수
* Company 연결
* SecurityConfig
* Swagger
* 단위·통합 테스트

### 2차: Job Bookmark

다음 범위를 별도 Issue와 PR로 구현합니다.

* 북마크 등록
* 북마크 해제
* 내 북마크 목록
* 중복 북마크 방지
* 북마크 수 집계
* 동시성·Unique Constraint 테스트

### 3차: 후속 도메인 연동

각 도메인이 준비된 뒤 별도 Issue로 진행합니다.

* File 첨부파일 연동
* Form/Application 내부 지원 연동
* AI Analysis 연동
* Collector 등록 Interface
* Search 색인 Event
* Company 상세의 `openJobs`
* Company 삭제 시 공개 공고 존재 여부 검증

각 PR은 하나의 Issue 범위만 처리해 주세요.

## 5. 기본 패키지 구조

현재 프로젝트의 `domain`, `global` 구조를 유지합니다.

```text
domain/job/
├── controller/
├── dto/
├── entity/
│   └── type/
├── exception/
├── repository/
├── service/
│   └── impl/
└── event/
```

필요한 경우 공고 출처를 Job 하위 책임으로 둡니다.

```text
domain/job/
├── source/
│   ├── entity/
│   ├── repository/
│   └── dto/
```

무조건 이 구조를 새로 만들지는 말고 현재 저장소의 Package Architecture 규칙과 기존 Entity 구조를 먼저 확인해 주세요.

Service는 Interface와 구현체로 분리하고 트랜잭션은 구현체에 둡니다.

조회 Service에는 `@Transactional(readOnly = true)`를 적용합니다.

Entity를 Controller 응답으로 직접 반환하지 않습니다.

## 6. Company 연동 방향

공고는 반드시 존재하고 삭제되지 않은 기업을 참조해야 합니다.

공고 등록·수정 시 `companyId`의 유효성을 검증해 주세요.

다만 다음 방식은 사용하지 마세요.

* Job에서 `CompanyRepository` 직접 주입
* Job에서 Company 내부 Entity 수정
* Company 내부 구현 패키지 강제 공개
* ModularityTest를 피하기 위한 임의 Package 변경

Company가 제공하는 공개 Service 또는 Query Interface를 통해 기업 존재 여부와 공개 요약 정보를 받아야 합니다.

현재 Company 공개 기능이 Job에서 사용하기 적절하지 않거나 Spring Modulith가 접근을 막으면 임의로 우회하지 말고 먼저 공유해 주세요. 필요한 경우 Company에 다음과 같은 공개 Query 계약을 추가하는 별도 Issue가 필요합니다.

* 삭제되지 않은 기업 존재 확인
* ID 기반 기업 공개 요약 조회
* 기업명, 유형, MOU 상태, 로고 URL 조회

Job 응답에서는 Company Entity가 아니라 별도의 `CompanySummaryResponse` 형태를 사용합니다.

## 7. Job Core API

### 공고 등록·임시저장

```text
POST /api/v1/admin/jobs
```

주요 요청 필드:

* `companyId`
* `sourceId`
* `formId`
* `title`
* `content`
* `postingType`
* `applicationMethod`
* `startDate`
* `endDate`
* `targetGrades`
* `externalUrl`
* `fileIds`
* `status`

성공 응답은 `201 Created`입니다.

DRAFT 상태의 임시저장은 게시 상태보다 Validation을 완화할 수 있습니다.

단, PUBLISHED 상태로 만들 때는 게시 필수값을 모두 검증해야 합니다.

### 공고 내용 수정

```text
PATCH /api/v1/admin/jobs/{jobId}
```

전달된 필드만 수정합니다.

미전달과 명시적 `null`을 구분해야 하는 필드가 있는지 먼저 확인해 주세요. Nullable DTO만 사용하면 값을 비우는 작업과 값 유지가 구분되지 않습니다.

Status 변경은 이 Endpoint에서 함께 처리하지 않고 상태 변경 Endpoint로 분리하는 것을 권장합니다.

### 공고 상태 변경

```text
PATCH /api/v1/admin/jobs/{jobId}/status
```

요청:

```json
{
  "status": "JobStatus"
}
```

상태 Enum:

```text
DRAFT
PUBLISHED
CLOSED
DELETED
```

권장 기본 전이는 다음과 같습니다.

```text
DRAFT -> PUBLISHED
DRAFT -> DELETED
PUBLISHED -> CLOSED
PUBLISHED -> DELETED
CLOSED -> DELETED
```

`CLOSED -> PUBLISHED`, `DELETED -> 다른 상태` 복구는 현재 명세에 확정되지 않았으므로 임의로 허용하지 마세요.

잘못된 상태 전이는 `JOB_STATUS_TRANSITION_INVALID`로 처리합니다.

삭제는 실제 행을 삭제하지 않고 이력을 보존해야 합니다. 기존 Entity가 `deletedAt`과 `JobStatus.DELETED`를 모두 가진다면 두 값의 변경을 하나의 트랜잭션에서 일관되게 처리합니다.

기존 북마크와 지원 이력은 삭제하지 않습니다.

### 공고 검색·목록

```text
GET /api/v1/jobs
```

필터:

* `query`
* `sourceId`
* `companyType`
* `postingType`
* `techStackIds`
* `status`
* `openOnly`
* `sort`
* `page`
* `size`

기본 페이지는 `page=0`, `size=20`, 최대 `size=100`입니다.

목록은 항상 안정적인 정렬 기준을 가져야 합니다. 동일한 정렬값에서는 `jobId`를 보조 정렬값으로 사용해 페이지 간 중복·누락을 방지해 주세요.

검색어의 `%`, `_`, `\`가 LIKE Wildcard로 잘못 처리되지 않게 Escape해야 합니다.

다만 Elasticsearch 기반 전문 검색은 Search 도메인의 후속 작업입니다. 이번 Job Core에서는 PostgreSQL을 이용한 기본 제목·기업명 검색과 필터만 구현합니다.

`techStackIds`는 기술스택 연결 구조가 현재 존재하는지 확인한 뒤 구현합니다. 테이블이나 공개 계약이 없으면 임의 Entity를 대량 추가하지 말고 제외 범위를 공유해 주세요.

일반 사용자의 목록에는 기본적으로 공개된 공고만 노출합니다.

* `PUBLISHED`
* Soft Delete되지 않음
* 비공개 상태가 아님

`openOnly=true`라면 마감되지 않은 공개 공고만 반환합니다.

### 공고 상세·조회수 증가


```text
GET /api/v1/jobs/{jobId}
```

성공 응답은 `200 OK`입니다.

존재하지 않거나 Soft Delete된 공고는 `JOB_NOT_FOUND`로 처리합니다.

일반 사용자가 DRAFT 또는 비공개 공고를 조회하려 하면 `JOB_NOT_VISIBLE`로 처리합니다.

공고 상세 화면을 열 때마다 조회수를 증가시킵니다. 동일 사용자 또는 동일 IP의 중복 조회도 현재 정책상 허용합니다.

조회수는 읽기 후 `+1` 저장 방식으로 처리하지 말고 DB 증가 Query 등 원자적인 방법을 사용해 동시 요청에서 증가분이 유실되지 않게 해 주세요.

조회수 증가와 상세 조회 응답이 실패했을 때의 트랜잭션 범위도 테스트해 주세요.

### 공고 출처 목록

```text
GET /api/v1/job-sources
```

Query Parameter:

* `activeOnly`

응답:

* `sourceId`
* `name`
* `sourceType`
* `active`

Collector가 이후 사용할 출처와 수동 등록 출처를 구분할 수 있어야 합니다.

다만 `JobSourceType`의 Enum 값은 API 공통 Enum 목록에 아직 확정되어 있지 않으므로 임의로 결정하지 말고 작업 전에 확인 요청해 주세요.

## 8. Bookmark API

### 북마크 등록

```text
PATCH /api/v1/jobs/{jobId}/bookmark
```

응답:

* `jobId`
* `bookmarked`
* `bookmarkCount`

같은 사용자가 같은 공고를 여러 번 북마크할 수 없어야 합니다.

DB에 `(member_id, job_id)` Unique Constraint를 적용해 동시 요청에서도 중복 행이 생성되지 않게 해 주세요.

이미 북마크한 공고에 다시 요청할 경우 별도 중복 오류보다 현재 상태를 반환하는 멱등 방식으로 구현하는 것을 권장합니다.

### 북마크 해제

```text
DELETE /api/v1/jobs/{jobId}/bookmark
```

성공 응답은 `204 No Content`이며 Body를 반환하지 않습니다.

북마크가 없다면 API 명세에 따라 `BOOKMARK_NOT_FOUND`를 반환합니다.

### 내 북마크 목록

```text
GET /api/v1/me/job-bookmarks
```

필터:

* `query`
* `postingType`
* `companyType`
* `sort`
* `page`
* `size`

마감된 공고는 목록에서 제거하지 않고 `CLOSED` 상태로 표시합니다.

Soft Delete된 공고 노출 여부는 현재 명세가 불분명하므로 임의로 결정하지 말고 확인해 주세요. 기본 방향은 Soft Delete된 공고를 사용자 목록에서 숨기되 북마크 데이터 자체는 보존하는 것입니다.

북마크 수는 조회 시 전체 행을 반복해서 세는 방식보다 집계 Query 또는 일관된 Counter 정책을 사용해 주세요.

## 9. Validation 정책

공고 게시 시 최소한 다음을 검증합니다.

* 기업이 존재하고 삭제되지 않았는가
* 제목이 비어 있지 않은가
* Markdown 본문이 비어 있지 않은가
* 공고 유형이 존재하는가
* 지원 방식이 존재하는가
* 대상 학년이 유효한가
* 시작일이 종료일보다 늦지 않은가
* 외부 지원이면 유효한 외부 URL이 있는가
* 내부 지원이면 필요한 Form 연결이 있는가
* MOU 공고 정책을 충족하는가

`targetGrades`는 고등학교 학년 범위인 1, 2, 3만 허용하고 중복값을 정규화하거나 Validation 오류로 처리해 주세요.

외부 URL은 `http`, `https` Scheme만 허용하는 방향을 권장합니다.

Job 서버가 외부 URL로 직접 요청하지 않는다면 SSRF 문제로 과장하지 말고 입력 형식과 클라이언트 이동 안전성만 검증합니다.

공고 임시저장에서는 일부 필수값이 없을 수 있지만 PUBLISHED 전환 시에는 모든 게시 필수값을 다시 검증해야 합니다.

## 10. 지원 방식과 Form 경계

지원 방식 Enum:

```text
INTERNAL
EXTERNAL
```

권장 규칙:

* `EXTERNAL`: `externalUrl` 필수, `formId` 불필요
* `INTERNAL`: `formId` 필수, `externalUrl` 불필요

다만 Form 도메인이 아직 구현되지 않았다면 Form Entity나 Repository를 Job 내부에 임시로 만들지 마세요.

Job은 `formId`를 Nullable 식별자로 보관하거나 연동을 후속 작업으로 분리해야 합니다.

`JOB_FORM_REQUIRED`를 현재 작업에서 실제로 검증할 수 없다면 게시 상태 지원 범위를 제한하고 PR 본문에 제외 이유를 명시해 주세요.

실제 지원 가능 여부, MOU 공고의 해당 연도 3학년 판단, 지원서 제출은 Application/Form 도메인이 담당합니다.

## 11. AI Analysis 경계

다음 API는 AI Analysis 도메인이 준비된 뒤 별도 Issue로 구현합니다.

```text
POST /api/v1/jobs/{jobId}/ai-reanalysis
```

Job 개발자가 직접 LLM API를 호출하거나 AI 결과 Entity를 임의로 추가하면 안 됩니다.

AI Analysis가 준비되면 Job은 다음 방식으로 협력합니다.

* 공고 최초 게시 Event 발행
* AI Analysis가 공고 내용 조회
* AI 분석 상태와 결과 조회
* 재분석 요청을 AI Analysis에 전달
* 최대 재분석 3회
* 처리 중 중복 요청 차단
* 비동기 작업 접수 시 202 반환

AI 장애가 공고 등록·게시·조회 자체를 실패시키면 안 됩니다.

현재 응답 명세에는 `aiAnalysis`와 `aiRequestAccepted`가 있지만 AI 도메인 미구현 상태에서 가짜 데이터나 성공 상태를 반환하지 마세요. Nullable 처리 또는 Endpoint 단계적 제공 여부를 먼저 확인해야 합니다.

## 12. File 경계

현재 Request와 Response에는 `fileIds`, `files`가 포함되어 있습니다.

File 도메인이 구현되지 않았다면 다음을 하지 마세요.

* Job 내부에 파일 업로드 구현
* 임시 파일 Entity 생성
* 실제 다운로드 URL 조립
* 접근 권한 검증 우회
* 존재하지 않는 파일을 정상 파일처럼 반환

File 도메인 준비 전 `fileIds`와 `files`를 어떻게 처리할지는 별도 결정이 필요합니다.

초기 Job Core에서는 첨부파일을 제외하거나 빈 목록으로 제공할 수 있지만, 프론트엔드 계약이 바뀌므로 반드시 작업 지시자 확인 후 진행해 주세요.

## 13. 권한

현재 역할은 다음 세 종류만 사용합니다.

```text
STUDENT
TEACHER
DEVELOPER
```

일반 공고 조회 API는 인증된 사용자에게 제공합니다.

```text
GET /api/v1/jobs
GET /api/v1/jobs/{jobId}
GET /api/v1/job-sources
```

관리 API는 별도 Controller로 분리합니다.

```text
/api/v1/admin/jobs/**
```

현재 문서에는 권한 충돌이 있습니다.

* 기능명세서: 교사와 개발자가 공고 작성·수정·삭제
* API 명세서의 등록 API: 개발자만 가능
* API 명세서의 수정·상태 변경: 교사와 개발자
* PRD: 교사가 MOU 공고를 등록하고 관리

추천 방향은 공고 등록·수정·상태 변경을 `TEACHER`, `DEVELOPER`가 모두 사용할 수 있게 통일하는 것입니다.

다만 API 계약 변경에 해당하므로 임의로 적용하지 말고 작업 시작 전에 작업 지시자에게 최종 확인을 받아 주세요.

관리 권한은 SecurityConfig와 Service 계층에서 모두 검증합니다.

교사가 자신이 담당하지 않은 공고도 수정할 수 있는지, 담당자만 수정할 수 있는지는 현재 확정되지 않았습니다. `NOT_JOB_MANAGER`의 정확한 기준을 임의로 만들지 말고 확인해 주세요.

## 14. 아직 확정이 필요한 사항

다음 항목은 구현 전에 작업 지시자에게 확인해야 합니다.

1. 공고 등록 권한이 개발자만인지, 교사와 개발자인지
2. 교사가 모든 공고를 관리할 수 있는지, 담당 공고만 가능한지
3. `JobSourceType` Enum 값
4. `JobSort` Enum 값과 기본 정렬
5. Status 허용 전이
6. DRAFT 임시저장 시 최소 필수값
7. `PUBLISHED` 전환 시 필수값
8. File 도메인 전 `fileIds`, `files` 처리
9. Form 도메인 전 `formId`, 내부 지원 처리
10. AI 도메인 전 `aiAnalysis`, `aiRequestAccepted` 처리
11. 기술스택 연결 테이블과 `techStackIds` 필터 처리
12. Soft Delete된 공고의 북마크 목록 노출 여부
13. 조회수 증가 실패가 상세 조회까지 실패시킬지
14. 상세 조회 응답의 `canApply` 계산 시점
15. 공개 공고 목록과 관리자용 전체 상태 목록을 같은 API로 처리할지

이 항목들은 임의로 추론해 구현하지 마세요.

확정되지 않은 사항이 현재 Issue 완료에 반드시 필요하면 구현을 중단하고 질문해 주세요. 후속 기능이라면 제외 범위로 명시하고 진행해 주세요.

## 15. 예외 처리

기존 `global.error` 구조를 사용합니다.

필요한 Job Error Code 예시:

```text
JOB_NOT_FOUND
JOB_NOT_VISIBLE
JOB_VALIDATION_FAILED
JOB_STATUS_TRANSITION_INVALID
JOB_FORM_REQUIRED
NOT_JOB_MANAGER
SORT_NOT_SUPPORTED
BOOKMARK_NOT_FOUND
DUPLICATE_BOOKMARK
JOB_SOURCE_NOT_FOUND
COMPANY_NOT_FOUND
```

기존 Error Code와 중복되는 코드를 새로 만들지 마세요.

Validation 오류가 공통 `VALIDATION_FAILED`로 변환되는지, Job 전용 `JOB_VALIDATION_FAILED`가 필요한지 API 명세와 기존 GlobalExceptionHandler를 대조해 주세요.

내부 Exception Message, 입력값, Stack Trace를 API 응답에 노출하지 마세요.

## 16. Swagger/OpenAPI

모든 Endpoint와 DTO를 프론트엔드가 바로 사용할 수 있을 정도로 문서화해 주세요.

반드시 포함할 내용:

* `@Tag`
* `@Operation`
* `@ApiResponses`
* 인증 요구사항
* Path Parameter 설명
* Query Parameter 설명
* DTO `@Schema`
* Nullable 여부
* Enum 값
* 기본값
* 최대 페이지 크기
* 성공 Status
* Error Code
* DRAFT와 PUBLISHED Validation 차이
* 상태 전이
* 외부·내부 지원 방식 차이

`docs/ai/openapi-documentation.md`를 따르고 `OpenApiDocumentationTest`를 통과해야 합니다.

Swagger 설명과 실제 SecurityConfig, Validation, Jackson 응답이 일치해야 합니다.

## 17. 테스트 요구사항

### Controller 테스트

* 공고 등록 201
* 공고 수정 200
* 상태 변경 200
* 목록 200
* 상세 200
* 북마크 등록 200
* 북마크 해제 204
* 내 북마크 목록 200
* 인증 없음 401
* 권한 부족 403
* Validation 400
* 존재하지 않는 공고 404
* 비공개 공고 조회 실패
* 잘못된 상태 전이 409
* 잘못된 정렬 400
* 공통 응답 Wrapper

### Service 테스트

* DRAFT 임시저장
* 게시 필수값 검증
* 기업 존재 검증
* 날짜 범위 검증
* 외부·내부 지원 방식 검증
* 부분 수정
* 상태 전이
* Soft Delete
* 삭제 후 조회 불가
* 조회수 증가
* 북마크 멱등성
* 북마크 해제
* 사용자별 북마크 여부
* Pagination
* 공개 상태 필터

### Repository 통합 테스트

PostgreSQL과 Testcontainers로 다음을 검증해 주세요.

* Soft Delete 제외
* 공개 공고 필터
* LIKE Wildcard Escape
* 날짜·상태 필터
* 안정적인 Pagination
* 조회수 원자 증가
* 중복 북마크 Unique Constraint
* 동시 북마크 요청
* Company FK
* Migration 적용

단위 테스트에서 Mock에 Query Parameter가 전달됐다는 것만 확인하고 실제 JPQL·SQL 동작을 검증했다고 판단하지 마세요.

## 18. 완료 조건

각 Issue와 PR은 다음을 만족해야 합니다.

* Issue 범위의 Endpoint 구현
* Request/Response DTO 분리
* Entity 직접 반환 금지
* Service Interface와 구현체 분리
* 조회 Transaction `readOnly = true`
* Soft Delete 적용
* Company 경계 준수
* 다른 도메인 Repository 직접 참조 금지
* Error Code와 Exception 구현
* SecurityConfig 최소 변경
* Swagger 문서화
* Controller 테스트
* Service 테스트
* 필요한 Repository 통합 테스트
* Migration 신규 파일 사용
* `ModularityTest` 통과
* `PackageArchitectureTest` 통과
* `OpenApiDocumentationTest` 통과
* 전체 CI 통과

검증 명령:

```bash
export LANG=C.UTF-8
export LC_ALL=C.UTF-8
export LANGUAGE=C.UTF-8
./gradlew spotlessCheck
./gradlew detekt
./gradlew test
./gradlew integrationTest
./gradlew check
./gradlew clean test build
docker compose config --quiet
docker build -t geti-server-app:job .
```

Docker를 사용할 수 없는 환경이면 Integration Test와 Docker Build를 실행하지 못한 이유를 PR에 명시하고 GitHub Actions CI 결과를 반드시 확인해 주세요.

## 19. 협업 규칙

* 작업 전 Issue 생성
* Issue 승인 후 브랜치 생성
* Base는 최신 `develop`
* 한 Issue당 한 Branch
* 한 PR에는 하나의 작업 범위
* PR은 처음에 Draft로 생성
* 최소 두 번 Self Review
* CI 전체 통과 후 리뷰 요청
* `develop` 직접 Push 금지
* 자동 Merge 금지
* 관련 없는 리팩터링 금지
* 기존 Migration 수정 금지
* Secret과 개인정보 Commit 금지
* 문서와 구현이 다르면 임의 판단 금지

PR 본문에는 다음을 반드시 작성해 주세요.

* 구현한 API
* 제외한 API
* 권한
* 상태 전이
* Company 연동 방식
* File·Form·AI 제외 범위
* Migration
* 테스트 결과
* Swagger 검증
* Architecture 검증
* 후속 Issue
