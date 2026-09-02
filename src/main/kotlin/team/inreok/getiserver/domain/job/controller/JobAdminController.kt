package team.inreok.getiserver.domain.job.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import team.inreok.getiserver.domain.job.dto.JobAdminListResponse
import team.inreok.getiserver.domain.job.dto.JobCreateRequest
import team.inreok.getiserver.domain.job.dto.JobDetailResponse
import team.inreok.getiserver.domain.job.dto.JobStatusUpdateRequest
import team.inreok.getiserver.domain.job.dto.JobUpdateRequest
import team.inreok.getiserver.domain.job.entity.type.JobStatus
import team.inreok.getiserver.domain.job.service.JobService
import team.inreok.getiserver.global.openapi.BEARER_AUTH_SCHEME
import team.inreok.getiserver.global.web.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

@Tag(
    name = "Job - 공고 관리",
    description = "채용 공고를 등록·수정하고 상태를 변경한다. 필요 권한: 교사, 개발자.",
)
@SecurityRequirement(name = BEARER_AUTH_SCHEME)
// SecurityConfig가 이 경로를 TEACHER 또는 DEVELOPER 권한 필수로 지정하므로, 여기 도달했다는 것은
// 이미 유효한 Access Token으로 인증되고 두 역할 중 하나를 가졌다는 뜻이다. 담당자 본인만 수정할 수
// 있는지(NOT_JOB_MANAGER)는 기준이 확정되지 않아 이번 범위에서 검증하지 않는다(Issue #60).
@RestController
@RequestMapping("/api/v1/admin/jobs")
class JobAdminController(
    private val jobService: JobService,
) {
    @Operation(
        summary = "관리자 공고 목록 조회",
        description = """
            관리자 공고 관리 화면에서 사용할 공고 목록을 조회한다. DRAFT, PUBLISHED, CLOSED 공고를
            포함하며 `status`를 지정하면 해당 상태를 조회할 수 있다. `status`를 생략한 기본 목록은
            삭제된 공고를 제외한다. `query`는 제목 부분 일치 검색이고, page는 0부터 시작하며 size는
            최대 100이다. 기본 정렬은 최신 생성 시각순이며 같은 시각에는 공고 ID 내림차순을 적용한다.
            교사와 개발자만 사용할 수 있다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "조회 성공(결과가 없으면 빈 목록)"),
        SwaggerApiResponse(responseCode = "400", description = "잘못된 상태 값 또는 Pagination 파라미터"),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(responseCode = "403", description = "교사 또는 개발자 권한이 없음 (FORBIDDEN)"),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @GetMapping
    fun listJobs(
        @Parameter(description = "공고 제목 부분 일치 검색어(선택)", example = "백엔드")
        @RequestParam(required = false)
        query: String?,
        @Parameter(description = "공고 상태 필터(선택). 생략하면 삭제된 공고를 제외한다.", example = "DRAFT")
        @RequestParam(required = false)
        status: JobStatus?,
        @Parameter(
            description = "Pagination(page: 0부터 시작, size: 기본 20, 최대 100). sort 파라미터는 무시하고 최신 생성 시각순으로 조회한다.",
        )
        pageable: Pageable,
        authentication: Authentication,
    ): ApiResponse<JobAdminListResponse> =
        ApiResponse.of(jobService.listForAdmin(query, status, pageable, authentication.principal as Long))

    @Operation(
        summary = "공고 등록·임시저장",
        description = """
            공고를 등록한다. `status`에 DRAFT를 지정하면 임시저장으로 저장되어 제목·기업·공고
            유형·지원 방식만 있으면 되고, PUBLISHED를 지정하면 게시 필수값을 모두 검증한다.

            게시 필수값: 본문(content)이 비어 있지 않을 것, 외부 지원 URL이 http/https일 것.
            지원 방식이 INTERNAL이면 지원서 양식 연동 전이라 게시할 수 없다(JOB_FORM_REQUIRED).

            작성자는 Access Token에서 식별해 자동으로 기록한다. CLOSED와 DELETED는 이 API로
            지정할 수 없고 상태 변경 API를 사용한다.

            `fileIds`를 지정하면 본인이 FilePurpose=JOB_ATTACHMENT로 업로드하고 아직 연결하지
            않은 파일만 첨부파일로 연결할 수 있다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "201", description = "등록 성공"),
        SwaggerApiResponse(
            responseCode = "400",
            description =
                "제목 공백·대상 학년 범위·모집 기간 역전·외부 URL 형식 등 공고 규칙 위반(JOB_VALIDATION_FAILED), " +
                    "INTERNAL 공고 게시 시도(JOB_FORM_REQUIRED), 길이 등 요청 값 형식 오류(VALIDATION_FAILED), " +
                    "필수 Field 누락(MALFORMED_JSON), 첨부파일 목적 불일치(FILE_PURPOSE_MISMATCH), " +
                    "첨부파일 개수 초과(FILE_COUNT_EXCEEDED)",
        ),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(
            responseCode = "403",
            description = "교사 또는 개발자 권한이 없음 (FORBIDDEN), 본인이 업로드하지 않은 첨부파일 (FILE_NOT_OWNED)",
        ),
        SwaggerApiResponse(
            responseCode = "404",
            description = "기업이 없거나 삭제됨 (COMPANY_NOT_FOUND), 존재하지 않는 첨부파일 (FILE_NOT_FOUND)",
        ),
        SwaggerApiResponse(responseCode = "409", description = "이미 다른 곳에 연결된 첨부파일 (FILE_ALREADY_LINKED)"),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createJob(
        @Valid @RequestBody request: JobCreateRequest,
        authentication: Authentication,
    ): ApiResponse<JobDetailResponse> {
        val memberId = authentication.principal as Long
        return ApiResponse.of(jobService.create(request, memberId))
    }

    @Operation(
        summary = "공고 부분 수정",
        description = """
            요청 Body에 전달한 Field만 수정한다(부분 수정, PATCH). 전달하지 않았거나 null인 Field는
            기존 값을 유지한다. 값을 비우는 기능은 이번 범위에서 지원하지 않는다.

            기업, 공고 유형, 지원 방식은 이 API로 변경할 수 없고 상태는 상태 변경 API로 분리되어
            있다. 이미 게시된 공고를 수정하면 수정 결과가 게시 필수값을 계속 만족하는지 다시
            검증한다.

            `fileIds`를 전달하지 않으면 기존 첨부파일을 유지한다. 전달하면(빈 배열 포함) 그 목록을
            최종 상태로 취급해 기존 연결을 모두 해제한 뒤 다시 연결한다 — 유지할 파일도 다시
            포함해야 하며, 본인이 업로드한 파일만 연결할 수 있다(다른 관리자가 업로드한 기존
            첨부는 본인이 아니면 재전송해도 FILE_NOT_OWNED로 거부된다).
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "수정 성공, 수정된 전체 Field를 반환"),
        SwaggerApiResponse(
            responseCode = "400",
            description =
                "공고 규칙 위반(JOB_VALIDATION_FAILED), 게시 상태 공고가 게시 필수값을 잃음" +
                    "(JOB_VALIDATION_FAILED), 요청 값 형식 오류(VALIDATION_FAILED), " +
                    "첨부파일 목적 불일치(FILE_PURPOSE_MISMATCH), 첨부파일 개수 초과(FILE_COUNT_EXCEEDED)",
        ),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(
            responseCode = "403",
            description = "교사 또는 개발자 권한이 없음 (FORBIDDEN), 본인이 업로드하지 않은 첨부파일 (FILE_NOT_OWNED)",
        ),
        SwaggerApiResponse(
            responseCode = "404",
            description = "공고가 없거나 삭제됨 (JOB_NOT_FOUND), 존재하지 않는 첨부파일 (FILE_NOT_FOUND)",
        ),
        SwaggerApiResponse(responseCode = "409", description = "이미 다른 곳에 연결된 첨부파일 (FILE_ALREADY_LINKED)"),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @PatchMapping("/{jobId}")
    fun updateJob(
        @Parameter(description = "수정할 공고 ID", example = "1") @PathVariable jobId: Long,
        @Valid @RequestBody request: JobUpdateRequest,
        authentication: Authentication,
    ): ApiResponse<JobDetailResponse> =
        ApiResponse.of(jobService.update(jobId, request, authentication.principal as Long))

    @Operation(
        summary = "공고 상태 변경",
        description = """
            공고를 게시·마감·삭제한다. 허용되는 상태 전이는 다음과 같고 그 외(마감·삭제된 공고의
            복구, 동일 상태로의 변경 포함)는 409로 거부한다.

            ```
            DRAFT     -> PUBLISHED | DELETED
            PUBLISHED -> CLOSED    | DELETED
            CLOSED    -> DELETED
            ```

            PUBLISHED로 바꿀 때는 게시 필수값을 모두 검증한다. DELETED는 Soft Delete로 처리해
            실제 행을 지우지 않으므로 기존 북마크와 지원 이력이 보존된다. DELETED로 전이하면
            첨부파일 연결도 함께 해제한다(Storage의 실제 파일은 지우지 않는다).
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "상태 변경 성공"),
        SwaggerApiResponse(
            responseCode = "400",
            description = "게시 필수값 미충족(JOB_VALIDATION_FAILED), INTERNAL 공고 게시 시도(JOB_FORM_REQUIRED)",
        ),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(responseCode = "403", description = "교사 또는 개발자 권한이 없음 (FORBIDDEN)"),
        SwaggerApiResponse(responseCode = "404", description = "공고가 없거나 삭제됨 (JOB_NOT_FOUND)"),
        SwaggerApiResponse(responseCode = "409", description = "허용되지 않은 상태 전이 (JOB_STATUS_TRANSITION_INVALID)"),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @PatchMapping("/{jobId}/status")
    fun changeJobStatus(
        @Parameter(description = "상태를 변경할 공고 ID", example = "1") @PathVariable jobId: Long,
        @Valid @RequestBody request: JobStatusUpdateRequest,
        authentication: Authentication,
    ): ApiResponse<JobDetailResponse> =
        ApiResponse.of(jobService.changeStatus(jobId, request, authentication.principal as Long))

    @Operation(
        summary = "관리자용 공고 상세 조회",
        description = """
            모든 상태(DRAFT, PUBLISHED, CLOSED, DELETED)의 공고를 조회한다. 공개 상세 조회와 달리
            조회수를 증가시키지 않으므로 관리자가 확인해도 통계가 왜곡되지 않는다.

            임시저장한 공고를 다시 열어보거나 삭제된 공고의 이력을 확인할 때 사용한다.

            `files`(첨부파일 목록)는 공개 상태(PUBLISHED, CLOSED)가 아니면 등록자·담당 교사·
            개발자만 실제 목록을 볼 수 있고, 그 외 요청자에게는 빈 목록이 담긴다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "조회 성공"),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(responseCode = "403", description = "교사 또는 개발자 권한이 없음 (FORBIDDEN)"),
        SwaggerApiResponse(responseCode = "404", description = "공고가 존재하지 않음 (JOB_NOT_FOUND)"),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @GetMapping("/{jobId}")
    fun getJobForAdmin(
        @Parameter(description = "조회할 공고 ID", example = "1") @PathVariable jobId: Long,
        authentication: Authentication,
    ): ApiResponse<JobDetailResponse> = ApiResponse.of(jobService.getForAdmin(jobId, authentication.principal as Long))
}
