# Job Core 구현 명세 (1차 PR)

[`Job-domain-develop.md`](./Job-domain-develop.md) 작업 지시서를 저장소의 실제 상태(병합된 V2 Migration, Spring Modulith 경계, 기존 Company/Member 구현 패턴)에 맞춰 확정한 구현 명세다. 지시서 §14가 "구현 전에 작업 지시자에게 확인"하라고 남긴 15개 항목 중 이번 범위에 걸리는 것은 모두 아래에서 결정됐다.

이 문서는 **1차 Job Core PR 하나**를 다룬다. 2차 Bookmark는 §12에 방향만 적는다.

---

## 1. 전제: V2 Schema와 지시서 API 명세의 불일치

`jobs` 테이블과 `Job` Entity, `JobRepository`는 **이미 `develop`에 병합되어 있다**(`V2__create_core_domain_schema.sql:144-192`). 지시서의 API 필드 목록은 이와 다른 스키마를 전제로 작성되어 있었다.

| 지시서 API 필드 | V2 실제 Schema | 결정 |
| --- | --- | --- |
| `sourceId` + `job_sources` 테이블 | `jobs.source_name VARCHAR(255)` 만 존재 | **제외** |
| `targetGrades` (배열) | `jobs.target_grade INTEGER` (단일, `CHECK 1~3`) | **단일 `targetGrade`로 축소** |
| `formId` | 컬럼 없음 (`application_form_schema JSONB`만) | **제외** |
| `techStackIds` 필터 | job↔tech_stacks 연결 테이블 없음 | **제외** |
| `companyType` 필터 | Job이 `companies`를 조인해야 함 | **제외** |
| `fileIds` / `files` | `files.owner_type/owner_id` 다형적 참조만 존재 | **제외** |
| 북마크 | `member_job_preferences`, 소유는 `recommendation` 도메인 | **2차 PR, recommendation 소유** |

**해결 방향: 기존 스키마를 그대로 두고 API 범위를 줄인다. 이번 PR의 Flyway Migration은 0개다.** 지시서 `:38`("병합된 Migration은 수정하지 않습니다")과 `:325`("테이블이나 공개 계약이 없으면 임의 Entity를 대량 추가하지 말고 제외 범위를 공유")을 따른 결과다.

`Job.kt` Entity는 **수정하지 않는다.**

---

## 2. 확정된 결정 요약

| # | 항목 | 결정 | 지시서 근거 |
| --- | --- | --- | --- |
| 1 | 관리 API 권한 | `TEACHER` + `DEVELOPER` 모두 | §13, §14-1 |
| 2 | 담당자 검증 | **하지 않음.** `NOT_JOB_MANAGER` 미구현 | §14-2 |
| 3 | 목록 API | 공개 전용. 관리자 목록은 후속 | §14-15 |
| 4 | 관리자 상세 | `GET /api/v1/admin/jobs/{jobId}` 추가 | §7 "일반 사용자" 문구 |
| 5 | `INTERNAL` 지원 방식 | DRAFT까지만. 게시 시 `JOB_FORM_REQUIRED` | §10 `:466` |
| 6 | 조회수 | 같은 트랜잭션 + `@Modifying` 원자 증가 | §14-13 |
| 7 | 정렬 | `JobSort { LATEST, DEADLINE, VIEWS }`, 기본 `LATEST` | §14-4 |
| 8 | 부분 수정 | nullable DTO. 값 비우기 미지원 | `:253` |
| 9 | Company 연동 | `CompanyQuery` `@NamedInterface` 신규 | §6 |
| 10 | 목록 응답 | `global.web.PageResponse` | `.claude/rules/spring-boot.md` |
| 11 | 상태 전이 | 지시서 `:283-290` 그대로 | §14-5 |

---

## 3. 범위

### 구현하는 Endpoint (6개)

```text
POST   /api/v1/admin/jobs              201  등록·임시저장
PATCH  /api/v1/admin/jobs/{jobId}      200  내용 부분 수정
PATCH  /api/v1/admin/jobs/{jobId}/status 200 상태 변경 (게시·마감·삭제)
GET    /api/v1/admin/jobs/{jobId}      200  관리자 상세 (모든 상태)
GET    /api/v1/jobs                    200  공개 목록
GET    /api/v1/jobs/{jobId}            200  공개 상세 (조회수 +1)
```

### 이번 PR에서 제외 (PR 본문에 사유와 함께 명시)

```text
GET  /api/v1/job-sources          job_sources 테이블 부재
GET  /api/v1/admin/jobs           관리자 목록 (후속)
POST /api/v1/jobs/{id}/ai-reanalysis  AI Analysis 도메인 미구현
북마크 3개 Endpoint               2차 PR

요청/응답 필드: sourceId, targetGrades[], formId, fileIds, files,
               techStackIds, companyType 필터, aiAnalysis,
               aiRequestAccepted, canApply, bookmarked, bookmarkCount

기능: 외부 수집, Elasticsearch, LLM 호출, 추천, 지원서, 파일 업로드,
     Discord 전송, 자동 마감 Scheduler, 알림
```

---

## 4. 파일 목록

### `domain/company` (신규 2, 수정 2)

```text
domain/company/query/CompanyQuery.kt            신규  @NamedInterface Interface + CompanySummary
domain/company/service/impl/CompanyQueryImpl.kt 신규  CompanyQuery 구현
domain/company/repository/CompanyRepository.kt  수정  findAllByIdInAndDeletedAtIsNull 추가
```

> **구현 중 변경**: 처음에는 `CompanyServiceImpl`이 `CompanyQuery`를 함께 구현하게 했으나,
> detekt `TooManyFunctions`(임계값 11)를 넘겨 별도 `CompanyQueryImpl`로 분리했다. Module 밖에서
> 호출되는 유일한 통로라 책임도 분리하는 편이 맞다. `CompanyServiceImpl`은 변경하지 않았다.

```kotlin
// domain/company/query/CompanyQuery.kt
package team.inreok.getiserver.domain.company.query

@NamedInterface
interface CompanyQuery {
    /** 삭제되지 않은 기업의 공개 요약. 없거나 삭제됐으면 null. */
    fun findActiveSummary(companyId: Long): CompanySummary?

    /** 목록 응답의 N+1을 피하기 위한 배치 조회. 없는 ID는 결과에서 빠진다. */
    fun findActiveSummaries(companyIds: Collection<Long>): Map<Long, CompanySummary>
}

@NamedInterface
data class CompanySummary(
    val companyId: Long,
    val name: String,
)
```

`CompanyType`/`MouStatus`는 **담지 않는다.** 두 Enum이 `domain.company.entity.type`(비공개 패키지)에 있어 Job이 다시 내부 패키지에 의존하게 되기 때문이다. 프론트가 MOU 배지를 표시해야 하면 `job.postingType == MOU`로 판단한다.

`CompanyServiceImpl`이 `CompanyService`와 `CompanyQuery`를 함께 구현한다. Job은 `create`/`update`/`delete`에 접근할 수 없다.

### `domain/job` (신규 15)

```text
domain/job/controller/JobController.kt              공개 2개
domain/job/controller/JobAdminController.kt         관리 4개
domain/job/dto/JobCreateRequest.kt
domain/job/dto/JobUpdateRequest.kt
domain/job/dto/JobStatusUpdateRequest.kt
domain/job/dto/JobDetailResponse.kt
domain/job/dto/JobSummaryResponse.kt
domain/job/dto/JobSort.kt                           API 정렬 Enum
domain/job/dto/PublicJobStatus.kt                   공개 목록 status 필터 Enum
domain/job/exception/JobErrorCode.kt
domain/job/exception/JobNotFoundException.kt
domain/job/exception/JobNotVisibleException.kt
domain/job/exception/JobStatusTransitionInvalidException.kt
domain/job/exception/JobValidationFailedException.kt
domain/job/exception/JobFormRequiredException.kt
domain/job/exception/JobCompanyNotFoundException.kt
domain/job/service/JobService.kt                    Interface
domain/job/service/JobValidation.kt                 상태별 검증 규칙 (top-level 함수)
domain/job/service/LikePatternEscape.kt
domain/job/service/impl/JobServiceImpl.kt
domain/job/repository/JobRepository.kt              수정 (searchPublic, incrementViewCount 추가)
```

> **구현 중 변경**: 검증 로직을 `JobServiceImpl` 안에 두었더니 detekt `TooManyFunctions`(11)와
> `ThrowsCount`(함수당 2)에 걸렸다. detekt 설정을 완화하는 대신 `JobValidation.kt`로 분리하고
> 규칙 하나에 함수 하나가 대응하도록 나눴다.

**`JobSort`와 `PublicJobStatus`는 `entity/type`이 아니라 `dto`에 둔다.** 영속 대상이 아니라 API 계약이기 때문이다. `PackageArchitectureTest`는 `@Entity`와 `JpaRepository`만 제약하므로 위반이 아니다.

**`LikePatternEscape.kt`는 `domain/company/service/LikePatternEscape.kt`의 복제다.** 원본이 `internal fun`이고 `domain.company.service`가 Named Interface로 공개되지 않아 Job이 import하면 `ModularityTest`가 실패한다. AGENTS.md "관련 없는 Refactoring 금지"에 따라 이번 PR에서 `global`로 승격하지 않고, 후속 정리 후보로 §13에 남긴다.

### `global` (수정 2)

```text
global/security/SecurityConfig.kt    /api/v1/admin/jobs, /api/v1/jobs 규칙 추가
global/web/PageResponse.kt           @NamedInterface 추가 + @Schema 문서화
```

> **구현 중 발견**: `PageResponse`에는 `@NamedInterface`가 없어 Domain Module에서 참조하면
> `ModularityTest`가 실패했다(`ApiResponse`에는 있다). 규칙 문서가 지정한 `PageResponse`를
> 실제로 쓸 수 없는 상태였고, Company가 자체 `CompanySearchResponse`를 만든 이유로 보인다.
> `ApiResponse`와 동일하게 Class와 Companion에 `@NamedInterface`를 붙여 공개했다.

```kotlin
// 더 구체적인 admin 경로를 먼저 선언한다 (기존 companies 규칙과 동일한 순서 규칙)
authorize("/api/v1/admin/jobs", hasAnyRole("TEACHER", "DEVELOPER"))
authorize("/api/v1/admin/jobs/**", hasAnyRole("TEACHER", "DEVELOPER"))
authorize("/api/v1/jobs", authenticated)
authorize("/api/v1/jobs/**", authenticated)
```

---

## 5. API 계약

### 5.1 `POST /api/v1/admin/jobs` → 201

```jsonc
{
  "companyId": 1,                            // 필수
  "postingType": "GENERAL|MOU|SCHOOL",       // 필수
  "applicationMethod": "INTERNAL|EXTERNAL",  // 필수
  "title": "...",                            // 필수, 1~500, 공백만 불가
  "status": "DRAFT|PUBLISHED",               // 필수. CLOSED/DELETED는 거부
  "content": "마크다운 본문",                  // 선택 (PUBLISHED면 필수)
  "externalUrl": "https://...",              // 선택 (EXTERNAL+PUBLISHED면 필수), ≤2000
  "startDate": "2026-08-01T00:00:00",        // 선택
  "endDate": "2026-08-31T23:59:59",          // 선택
  "targetGrade": 3,                          // 선택, 1~3
  "capacity": 2,                             // 선택, > 0
  "firstComeServed": false                   // 선택, 기본 false
}
```

서버가 채우는 값: `createdByMemberId` = `authentication.principal as Long`, `viewCount` = 0, `publishedAt` = `status == PUBLISHED`이면 현재 시각.

항상 `null`로 두는 컬럼: `managerMemberId`, `sourceName`, `externalJobId`, `discordChannelKey`, `requiredSkills`, `applicationFormSchema`, `closedAt`, `deletedAt`.

`managerMemberId`를 요청으로 받지 않는 이유는 실존 회원 검증에 Member 모듈 경계를 또 넘어야 하고, 검증 없이 받으면 FK 위반이 500으로 새기 때문이다.

응답은 `ApiResponse<JobDetailResponse>`.

### 5.2 `PATCH /api/v1/admin/jobs/{jobId}` → 200

```jsonc
{
  "title": null, "content": null, "externalUrl": null,
  "startDate": null, "endDate": null,
  "targetGrade": null, "capacity": null, "firstComeServed": null
}
```

전달하지 않았거나 `null`인 필드는 **기존 값을 유지한다.** 값 비우기는 지원하지 않는다(`CompanyServiceImpl.applyChanges`와 동일한 패턴). 마감일을 없애야 하면 상태를 `CLOSED`로 바꿔 대응한다.

`companyId`, `postingType`, `applicationMethod`, `status`는 이 Endpoint로 바꿀 수 없다. 상태는 §5.3으로 분리한다(지시서 `:255`).

**대상 공고가 이미 `PUBLISHED`면 수정 후 값으로 게시 필수값을 재검증한다.** `DRAFT`면 완화 검증만 한다.

### 5.3 `PATCH /api/v1/admin/jobs/{jobId}/status` → 200

```jsonc
{ "status": "PUBLISHED" }
```

허용 전이 (지시서 `:283-288`):

```text
DRAFT     -> PUBLISHED    게시 필수값 전체 검증, publishedAt = now
DRAFT     -> DELETED      deletedAt = now
PUBLISHED -> CLOSED       closedAt = now
PUBLISHED -> DELETED      deletedAt = now
CLOSED    -> DELETED      deletedAt = now
```

그 외는 전부 `JOB_STATUS_TRANSITION_INVALID` (409). **동일 상태로의 전이도 거부한다**(지시서 전이표에 없음). `CLOSED -> PUBLISHED`와 `DELETED -> 무엇이든`은 지시서 `:290`이 명시적으로 금지했다.

삭제는 Soft Delete다. `deletedAt`과 `status = DELETED`를 **하나의 트랜잭션에서 함께** 기록한다(지시서 `:294`). 실제 행을 지우지 않으므로 북마크·지원 이력이 보존된다.

### 5.4 `GET /api/v1/admin/jobs/{jobId}` → 200

모든 상태(`DRAFT`/`PUBLISHED`/`CLOSED`/`DELETED`)를 조회한다. **조회수를 증가시키지 않고** `@Transactional(readOnly = true)`를 쓴다. 존재하지 않는 ID만 `JOB_NOT_FOUND`(404)다.

### 5.5 `GET /api/v1/jobs` → 200

Query Parameter:

| 이름 | 타입 | 기본값 | 설명 |
| --- | --- | --- | --- |
| `query` | String? | — | 제목 부분 일치(대소문자 무시). **기업명은 검색하지 않는다** |
| `postingType` | PostingType? | — | `GENERAL`/`MOU`/`SCHOOL` |
| `status` | PublicJobStatus? | — | `PUBLISHED`/`CLOSED`만 |
| `openOnly` | Boolean | `false` | 마감되지 않은 게시 공고만 |
| `sort` | JobSort | `LATEST` | `LATEST`/`DEADLINE`/`VIEWS` |
| `page` | Int | `0` | |
| `size` | Int | `20` | 최대 100 (`WebPageableConfig`가 전역 강제) |

기업명 검색이 빠진 이유는 `JobRepository`가 `companies`를 조인하지 않기로 했기 때문이다(모듈 경계). 지시서 `:323`의 "제목·기업명 검색" 중 제목만 구현한다.

`status=DRAFT`처럼 공개 대상이 아닌 값과 `sort=BOGUS`는 **Enum 바인딩 실패로 기존 `CommonErrorCode.TYPE_MISMATCH`(400)** 가 처리한다. 지시서 `:586`의 `SORT_NOT_SUPPORTED`는 `:593`("기존 Error Code와 중복되는 코드를 새로 만들지 마세요")에 따라 **만들지 않는다.**

공개 조건:

```sql
deleted_at IS NULL
AND status IN ('PUBLISHED', 'CLOSED')
AND (:openOnly = FALSE OR (status = 'PUBLISHED'
     AND (recruitment_ended_at IS NULL OR recruitment_ended_at > now)))
```

정렬 (모두 `id DESC` 보조 정렬로 안정화 — 지시서 `:319`):

```text
LATEST    published_at DESC NULLS LAST, id DESC
DEADLINE  recruitment_ended_at ASC NULLS LAST, id DESC
VIEWS     view_count DESC, id DESC
```

Controller는 `Pageable`(최대 size 100)과 `JobSort`를 **따로** 받고, Service가 `PageRequest.of(pageable.pageNumber, pageable.pageSize, sortOf(jobSort))`로 재조립한다. **`Pageable`이 들고 온 `sort`는 무시한다** — 내부 Entity 필드명이 정렬 키로 새는 것을 막기 위해서다. Swagger `@Parameter` 설명에 이 사실을 명시한다.

응답: `ApiResponse<PageResponse<JobSummaryResponse>>`

```jsonc
{
  "success": true,
  "data": {
    "data": [ /* JobSummaryResponse[] */ ],
    "meta": { "page": 0, "size": 20, "totalElements": 42,
              "totalPages": 3, "hasNext": true, "hasPrevious": false }
  },
  "meta": { "requestId": "..." }
}
```

`data.data` 중첩은 `.claude/rules/spring-boot.md`가 지정한 `PageResponse`를 그대로 쓴 결과다. Company의 `CompanySearchResponse`와 필드 이름이 다르다는 점을 PR 본문에 적는다.

### 5.6 `GET /api/v1/jobs/{jobId}` → 200

```kotlin
@Transactional  // readOnly 아님 — 조회수를 증가시킨다
override fun getPublicDetail(jobId: Long): JobDetailResponse {
    val job = jobRepository.findByIdAndDeletedAtIsNull(jobId)
        ?: throw JobNotFoundException(jobId)
    if (job.status != PUBLISHED && job.status != CLOSED) throw JobNotVisibleException(jobId)

    val company = job.companyId.let(companyQuery::findActiveSummary)
    // incrementViewCount는 영속성 Context를 우회하는 UPDATE라 job.viewCount가 낡은 값으로 남는다.
    // 응답에는 증가 후 값을 담아야 하므로 +1을 직접 반영한다.
    val response = JobDetailResponse.from(job, company, viewCount = job.viewCount + 1)
    jobRepository.incrementViewCount(jobId)
    return response
}
```

조회수 증가와 상세 조회는 **같은 트랜잭션**이다. 증가가 실패하면 조회도 실패한다(지시서 §14-13에 대한 결정). 단일 `UPDATE`이므로 실패 시나리오는 사실상 DB 장애뿐이다.

동일 사용자·동일 IP의 중복 조회도 증가시킨다(지시서 `:347`).

### 5.7 응답 DTO

```kotlin
data class JobSummaryResponse(
    val jobId: Long,
    val title: String,
    val postingType: PostingType,
    val applicationMethod: ApplicationMethod,
    val status: JobStatus,
    val company: CompanySummary?,   // 기업이 Soft Delete되면 null
    val startDate: LocalDateTime?,
    val endDate: LocalDateTime?,
    val targetGrade: Int?,
    val capacity: Int?,
    val firstComeServed: Boolean,
    val viewCount: Long,
    val publishedAt: LocalDateTime?,
)

data class JobDetailResponse(
    /* JobSummaryResponse의 모든 필드 + */
    val content: String?,           // body_markdown
    val externalUrl: String?,
    val createdAt: LocalDateTime?,
    val updatedAt: LocalDateTime?,
    val closedAt: LocalDateTime?,
)
```

`JobDetailResponse`는 공개 상세와 관리자 상세가 **공유한다.** 관리자는 `status` 필드로 `DRAFT`/`DELETED`를 구분한다. `deletedAt`은 노출하지 않는다.

`company`가 `null`일 수 있는 이유는 공고 등록 후 기업이 Soft Delete될 수 있기 때문이다(FK는 살아 있다). Swagger `@Schema(nullable = true)`로 명시한다.

Entity를 직접 반환하지 않는다. `Page<T>`를 그대로 반환하지 않는다.

---

## 6. Validation 정책

### DRAFT (완화)

스키마 NOT NULL이 최소 필수값을 정한다.

```text
companyId          필수 + 존재 + 미삭제      -> COMPANY_NOT_FOUND (404)
postingType        필수 (Enum)              -> VALIDATION_FAILED / TYPE_MISMATCH (400)
applicationMethod  필수 (Enum)              -> 동일
title              필수, trim 후 1~500      -> VALIDATION_FAILED (400)
status             DRAFT | PUBLISHED만      -> VALIDATION_FAILED (400)
```

값이 있으면 형식만 검증한다.

```text
startDate <= endDate (둘 다 있을 때)  -> JOB_VALIDATION_FAILED (400)
targetGrade in 1..3                  -> JOB_VALIDATION_FAILED (400)
capacity > 0                         -> JOB_VALIDATION_FAILED (400)
externalUrl scheme in (http, https), 길이 <= 2000 -> JOB_VALIDATION_FAILED (400)
```

### PUBLISHED (전체)

위 전부에 더해:

```text
content 가 비어 있지 않음                          -> JOB_VALIDATION_FAILED (400)
applicationMethod == EXTERNAL                     -> INTERNAL이면 JOB_FORM_REQUIRED (400)
externalUrl 필수 (http/https)                     -> JOB_VALIDATION_FAILED (400)
```

`targetGrade`와 `startDate`/`endDate`는 **게시에도 필수가 아니다.** 컬럼이 Nullable이고, 전 학년 대상이거나 상시 모집인 공고를 막을 근거가 명세에 없다. `endDate`가 `null`이면 `openOnly=true` 목록에 "마감 없음"으로 포함된다. — *가정, PR 본문에 명시*

`externalUrl`은 형식만 본다. Job 서버가 그 URL로 요청하지 않으므로 SSRF로 다루지 않는다(지시서 `:444`).

### 구현하지 못한 게시 검증 (PR 본문에 사유 명시)

```text
내부 지원이면 Form 연결   form_id 컬럼도 Form 도메인도 없음
                        -> INTERNAL의 PUBLISHED 전환 자체를 차단하는 것으로 대체
MOU 공고 정책            CompanySummary에 mouStatus를 담지 않기로 함
                        -> 검증 없음. 후속 Issue
```

---

## 7. Error Code

```kotlin
enum class JobErrorCode(...) : ErrorCode {
    JOB_NOT_FOUND(NOT_FOUND, "요청한 공고를 찾을 수 없습니다."),
    JOB_NOT_VISIBLE(FORBIDDEN, "공개되지 않은 공고입니다."),
    JOB_VALIDATION_FAILED(BAD_REQUEST, "공고 정보가 올바르지 않습니다."),
    JOB_STATUS_TRANSITION_INVALID(CONFLICT, "허용되지 않은 공고 상태 변경입니다."),
    JOB_FORM_REQUIRED(BAD_REQUEST, "내부 지원 공고는 지원서 양식이 연결되어야 게시할 수 있습니다."),
    COMPANY_NOT_FOUND(NOT_FOUND, "요청한 기업을 찾을 수 없습니다."),
}
```

`COMPANY_NOT_FOUND`를 `JobErrorCode`에도 정의하는 이유는 `CompanyErrorCode`가 `domain.company.exception`(비공개 패키지)에 있어 Job이 참조할 수 없기 때문이다. **Wire 상의 `code` 문자열과 HTTP Status(404)는 Company와 완전히 동일하므로 프론트 계약은 하나다.**

만들지 않는 코드와 사유:

```text
SORT_NOT_SUPPORTED   Enum 바인딩 실패가 CommonErrorCode.TYPE_MISMATCH(400)로 처리됨
NOT_JOB_MANAGER      담당자 검증을 하지 않기로 결정 (§2-2)
JOB_SOURCE_NOT_FOUND job-sources API 제외
BOOKMARK_NOT_FOUND   2차 PR
DUPLICATE_BOOKMARK   2차 PR (멱등 처리 방침이라 결국 안 쓸 가능성 높음)
```

모든 예외는 `global.error.BusinessException`을 상속한다. 내부 Exception Message, 입력값, Stack Trace를 응답에 노출하지 않는다.

---

## 8. Repository

```kotlin
interface JobRepository : JpaRepository<Job, Long> {
    fun findBySourceNameAndExternalJobId(sourceName: String, externalJobId: String): Job?  // 기존

    fun findByIdAndDeletedAtIsNull(id: Long): Job?

    @Query("""
        SELECT j FROM Job j
        WHERE j.deletedAt IS NULL
          AND j.status IN :statuses
          AND (:query IS NULL OR LOWER(j.title) LIKE LOWER(CONCAT('%', :query, '%')) ESCAPE '\')
          AND (:postingType IS NULL OR j.type = :postingType)
          AND (:openOnly = FALSE
               OR (j.status = team.inreok.getiserver.domain.job.entity.type.JobStatus.PUBLISHED
                   AND (j.recruitmentEndedAt IS NULL OR j.recruitmentEndedAt > :now)))
    """)
    fun searchPublic(
        @Param("statuses") statuses: Collection<JobStatus>,
        @Param("query") query: String?,
        @Param("postingType") postingType: PostingType?,
        @Param("openOnly") openOnly: Boolean,
        @Param("now") now: LocalDateTime,
        pageable: Pageable,
    ): Page<Job>

    @Modifying
    @Query("UPDATE Job j SET j.viewCount = j.viewCount + 1 WHERE j.id = :id")
    fun incrementViewCount(@Param("id") id: Long): Int
}
```

`:query`는 Service가 `escapeLikePattern`으로 미리 이스케이프해 전달한다. 공백만 보낸 경우는 "조건 없음"으로 취급한다(`CompanyServiceImpl.search`와 동일).

`@Modifying`에 `clearAutomatically`/`flushAutomatically`를 **쓰지 않는다.** 켜면 영속성 Context가 비워져 이미 읽은 `job`이 detach되기 때문이다. 대신 응답을 먼저 만들고 `+1`을 직접 반영한다(§5.6).

---

## 9. Service

```kotlin
interface JobService {
    fun create(request: JobCreateRequest, createdByMemberId: Long): JobDetailResponse
    fun update(jobId: Long, request: JobUpdateRequest): JobDetailResponse
    fun changeStatus(jobId: Long, status: JobStatus): JobDetailResponse
    fun getForAdmin(jobId: Long): JobDetailResponse
    fun getPublicDetail(jobId: Long): JobDetailResponse
    fun searchPublic(
        query: String?, postingType: PostingType?, status: PublicJobStatus?,
        openOnly: Boolean, sort: JobSort, pageable: Pageable,
    ): PageResponse<JobSummaryResponse>
}
```

Interface와 구현체를 분리하고 **Transaction은 구현체에만** 둔다(지시서 `:186`).

```text
create        @Transactional
update        @Transactional
changeStatus  @Transactional
getForAdmin   @Transactional(readOnly = true)
searchPublic  @Transactional(readOnly = true)
getPublicDetail  @Transactional            <- 조회수 증가 때문에 readOnly 불가
```

Controller는 비즈니스 로직을 갖지 않고, Repository를 직접 호출하지 않으며, Transaction을 시작하지 않는다.

`searchPublic`은 조회된 `Job`들의 `companyId`를 모아 `companyQuery.findActiveSummaries(ids)`를 **한 번만** 호출한다(N+1 방지).

---

## 10. Swagger / OpenAPI

`docs/ai/openapi-documentation.md`를 따르고 `OpenApiDocumentationTest`를 통과시킨다. 그 테스트가 실제로 강제하는 것:

```text
모든 Operation에 Tag, summary, description, 최소 하나의 성공 Response
모든 Path·Query Parameter에 설명
인증이 필요한 Endpoint에 Security Requirement 선언
```

**`OpenApiDocumentationTest`의 "인증 필요 Endpoint" 목록에 `jobs`를 추가해야 한다**(현재 `me, session, logout, members, companies, admin`만 검사). 테스트 수정도 이 PR 범위다.

Controller에 `@Tag`, `@SecurityRequirement(name = BEARER_AUTH_SCHEME)`, 메서드에 `@Operation` + `@ApiResponses`, DTO에 `@param:Schema`를 붙인다(Company 패턴 그대로).

문서에 반드시 담을 내용: Enum 값 전체, 기본값, 최대 페이지 크기 100, Nullable 여부, 성공 Status, Error Code, **DRAFT와 PUBLISHED의 Validation 차이**, **허용 상태 전이표**, **`INTERNAL`은 게시 불가**, `Pageable`의 `sort`가 무시되고 `JobSort`만 쓰인다는 사실.

---

## 11. 테스트

### Controller (`@WebMvcTest` + `@Import(SecurityConfig::class)` + `@EnableWebSecurity`)

`CompanyAdminControllerTest` 패턴을 그대로 쓴다 — `@MockitoBean`으로 `JobService`와 `JwtTokenProvider`를 대체하고, `SecurityMockMvcRequestPostProcessors.authentication(...)`으로 역할을 주입한다.

`JobAdminControllerTest`

```text
등록 201 + 응답 Wrapper(success/data/meta)
등록 400  title 공백, targetGrade=4, capacity=0, status=CLOSED
등록 400  JOB_FORM_REQUIRED (INTERNAL + PUBLISHED)
등록 404  COMPANY_NOT_FOUND
수정 200  전달한 필드만 반영
상태 변경 200
상태 변경 409  JOB_STATUS_TRANSITION_INVALID
관리자 상세 200 (DRAFT 조회 가능)
401  인증 없음
403  STUDENT 역할
403  인증만 있고 역할 없음
```

`JobControllerTest`

```text
목록 200 + PageResponse 구조(data.data / data.meta)
목록 400  ?status=DRAFT      (TYPE_MISMATCH)
목록 400  ?sort=BOGUS        (TYPE_MISMATCH)
상세 200 + viewCount가 증가 후 값
상세 404  JOB_NOT_FOUND
상세 403  JOB_NOT_VISIBLE (DRAFT)
401  인증 없음
```

### Service (`@ExtendWith(MockitoExtension::class)`, `CompanyServiceTest` 패턴)

```text
DRAFT 임시저장 — 선택 필드 없이 저장된다
게시 필수값 검증 — content 없이 PUBLISHED 거부
게시 필수값 검증 — EXTERNAL인데 externalUrl 없으면 거부
게시 필수값 검증 — INTERNAL이면 JOB_FORM_REQUIRED
기업 존재 검증 — findActiveSummary가 null이면 COMPANY_NOT_FOUND
날짜 범위 검증 — startDate > endDate 거부
targetGrade 범위, capacity 범위, externalUrl scheme 검증
부분 수정 — 전달하지 않은 필드가 유지된다
부분 수정 — PUBLISHED 공고는 수정 후 게시 필수값을 재검증한다
상태 전이 — 허용 5종 성공
상태 전이 — 금지 전이(CLOSED->PUBLISHED, DELETED->*, 동일 상태) 거부
Soft Delete — deletedAt과 status=DELETED가 함께 기록된다
삭제 후 공개 상세 조회 불가
조회수 — incrementViewCount가 호출되고 응답은 +1 값
관리자 상세 — 조회수가 증가하지 않는다
공개 목록 — searchPublic에 PUBLISHED/CLOSED만 전달된다
공개 목록 — openOnly=true 인자 전달
정렬 — JobSort별 Sort 객체가 id DESC 보조 정렬을 포함한다
목록 — findActiveSummaries가 1회만 호출된다 (N+1 방지)
```

### Repository 통합 (`src/integrationTest`, Testcontainers PostgreSQL)

`CompanyQueryIntegrationTest` 패턴(`@Testcontainers` + `@DataJpaTest` + `@ImportAutoConfiguration(FlywayAutoConfiguration::class)`)을 따른다.

```text
Soft Delete된 공고가 목록에서 제외된다
DRAFT/DELETED가 공개 목록에서 제외된다
LIKE Wildcard Escape — 제목의 %, _, \ 가 문자 그대로 매칭된다
openOnly — recruitment_ended_at 기준 필터
안정 Pagination — 동일 정렬값에서 page 0/1 사이 중복·누락이 없다
정렬 3종의 NULLS LAST 동작
조회수 원자 증가 — 동시 100회 후 정확히 +100
Company FK — 없는 company_id로 저장 시 제약 위반
```

지시서 `:680`대로 **Mock에 파라미터가 전달됐다는 것만으로 SQL 동작을 검증했다고 보고하지 않는다.**

### 구조 검증

```text
ModularityTest          job -> company.query 의존이 Named Interface로 허용되는지
PackageArchitectureTest Entity/Repository 배치
OpenApiDocumentationTest jobs Endpoint 문서 품질 (테스트 자체도 수정)
```

---

## 12. 2차 PR 방향 (Bookmark) — 이번 범위 아님

`member_job_preferences`는 `docs/architecture/erd.md:28`이 **`recommendation` 도메인**에 배정했고 `bookmarked`와 `exclusion`(추천 제외)을 함께 들고 있다. 그래서 북마크는 **`recommendation`이 소유한다.**

```text
domain/job/query/JobQuery.kt              신규 @NamedInterface (공고 존재·공개 여부)
domain/recommendation/controller/JobBookmarkController.kt
domain/recommendation/service/...

PATCH  /api/v1/jobs/{jobId}/bookmark    200  멱등 등록
DELETE /api/v1/jobs/{jobId}/bookmark    204  없으면 BOOKMARK_NOT_FOUND
GET    /api/v1/me/job-bookmarks         200
```

Migration은 0개다. 복합 PK `(member_id, job_id)`가 이미 중복 북마크를 막는다. **해제는 행 삭제가 아니라 `bookmarked = false`** 여야 `exclusion` 값이 유실되지 않는다.

Soft Delete된 공고의 북마크 목록 노출 여부(지시서 §14-12)는 2차 PR 시작 전에 확인이 필요하다.

---

## 13. 후속 Issue 후보

```text
GET /api/v1/job-sources + job_sources 테이블 (Collector 연동 시 함께)
GET /api/v1/admin/jobs 관리자 목록 (전체 상태)
targetGrades 다중 학년 (job_target_grades 테이블)
techStackIds 필터 (job_tech_stacks 테이블)
companyType 필터 + CompanySummary에 companyType/mouStatus 노출
기업명 검색 (query가 제목만 보는 문제)
MOU 공고 정책 게시 검증
formId + INTERNAL 게시 (Form 도메인 이후)
fileIds / files 첨부파일 (File 도메인 이후)
aiAnalysis / aiRequestAccepted / canApply (AI·Application 도메인 이후)
NOT_JOB_MANAGER 담당자 검증 + managerMemberId 입력
PATCH 값 비우기 (미전달 vs 명시적 null 구분)
escapeLikePattern을 global로 승격해 company/job 중복 제거
PageResponse vs CompanySearchResponse 응답 형태 통일
Company 삭제 시 공개 공고 존재 여부 검증 (COMPANY_HAS_ACTIVE_JOBS)
Company 상세의 openJobs
```

---

## 14. 검증

```bash
export LANG=C.UTF-8 LC_ALL=C.UTF-8 LANGUAGE=C.UTF-8
./gradlew spotlessApply
./gradlew spotlessCheck
./gradlew detekt
./gradlew test
./gradlew test --tests "*ModularityTest"
./gradlew test --tests "*PackageArchitectureTest"
./gradlew test --tests "*OpenApiDocumentationTest"
./gradlew integrationTest      # Docker 필요
./gradlew clean test build
docker compose config --quiet
docker build -t geti-server-app:job .
```

**현재 이 환경에서 Docker Desktop 엔진이 실행 중이 아니다**(`dockerDesktopLinuxEngine` 파이프 없음). `integrationTest`와 `docker build`를 실행하려면 Docker Desktop을 먼저 켜야 한다. 끝내 실행하지 못하면 PR 본문에 사유를 적고 GitHub Actions CI 결과를 반드시 확인한다(지시서 `:722`).

---

## 15. 협업

```text
작업 전 Issue 생성 -> 승인 -> Branch (feat/{issue-number}-job-core)
Base: 최신 develop
Draft PR로 시작, Self Review 2회, CI 전체 통과 후 리뷰 요청
develop 직접 Push 금지, 자동 Merge 금지, Force Push 금지
Commit Type은 영문 + 설명은 한글
```

PR 본문에 반드시 적을 것: 구현한 API 6개, 제외한 API와 **사유**, 권한(`TEACHER`+`DEVELOPER`, 담당자 검증 없음), 상태 전이표, Company 연동 방식(`CompanyQuery` Named Interface), File·Form·AI 제외 범위, **Migration 0개**, 테스트 결과, Swagger 검증, Architecture 검증, 후속 Issue, 그리고 **§1의 스키마 불일치와 §6의 가정**.
