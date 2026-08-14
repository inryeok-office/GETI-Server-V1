package team.inreok.getiserver.domain.program.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import team.inreok.getiserver.domain.program.dto.ProgramApplicationActionRequest
import team.inreok.getiserver.domain.program.dto.ProgramApplicationActionResponse
import team.inreok.getiserver.domain.program.dto.ProgramDetailResponse
import team.inreok.getiserver.domain.program.dto.ProgramListResponse
import team.inreok.getiserver.domain.program.entity.type.ProgramStatus
import team.inreok.getiserver.domain.program.entity.type.ProgramType
import team.inreok.getiserver.domain.program.service.ProgramService
import team.inreok.getiserver.global.openapi.BEARER_AUTH_SCHEME
import team.inreok.getiserver.global.web.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

@Tag(
    name = "Program - 프로그램 조회·신청",
    description = "게시된 특강·교육 프로그램을 조회하고 신청·취소한다. 필요 권한: 학생, 교사, 개발자(신청·취소는 학생만).",
)
@SecurityRequirement(name = BEARER_AUTH_SCHEME)
// SecurityConfig가 목록·상세 조회는 인증 필수로, POST .../application-actions는 STUDENT
// Role까지 요구하므로, 여기 도달했다는 것은 각 경로에 필요한 인증·Role 조건을 이미 만족했다는
// 뜻이다. 재학 여부(NOT_ENROLLED)는 Role이 아니라 학적 상태라 ProgramService가 별도로
// 판단한다(JobApplicationController와 동일한 원칙).
@RestController
@RequestMapping("/api/v1/programs")
class ProgramController(
    private val programService: ProgramService,
) {
    @Operation(
        summary = "프로그램 목록 조회",
        description = """
            게시 여부와 무관하게 조회할 수 있는 관리자용 목록이 아니라, `status` 필터를 그대로
            적용한 목록이다. `programType`, `status`로 필터링할 수 있고 `openOnly=true`면 PUBLISHED
            상태이면서 현재 신청 기간 안에 있는 프로그램만 반환한다(정원 마감 여부는 제외 조건에
            포함하지 않는다). `applied`는 요청한 학생의 신청 여부를 서버가 계산하며, 학생이 아닌
            요청은 항상 false다. 목록 기본값 page=0, size=20이며 최대 size=100이다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "조회 성공(결과가 없으면 빈 목록)"),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @GetMapping
    fun listPrograms(
        authentication: Authentication,
        @Parameter(description = "프로그램 유형 필터(선택)") @RequestParam(required = false) programType: ProgramType?,
        @Parameter(description = "프로그램 상태 필터(선택)") @RequestParam(required = false) status: ProgramStatus?,
        @Parameter(description = "모집 중인 프로그램만 조회(선택, 기본 false)")
        @RequestParam(required = false, defaultValue = "false")
        openOnly: Boolean,
        @Parameter(description = "Pagination(page: 0부터 시작, size: 기본 20, 최대 100)") pageable: Pageable,
    ): ApiResponse<ProgramListResponse> {
        val memberId = authentication.principal as Long
        return ApiResponse.of(programService.list(programType, status, openOnly, memberId, pageable))
    }

    @Operation(
        summary = "프로그램 상세 조회",
        description = """
            programId로 지정한 프로그램의 상세 정보를 조회하고 조회수를 1 증가시킨다. 요청한
            사용자 기준으로 `canApply`/`eligibilityReason`/`eligibilityMessage`/`availableActions`를
            서버가 계산해 함께 반환한다 — 클라이언트가 대상 학년·재학 여부·모집 기간·정원·신청
            여부를 직접 조합해 계산하지 않는다. 삭제된 프로그램은 410로 응답한다.

            `files`(본문 첨부파일 목록)는 게시(PUBLISHED)·마감(CLOSED) 상태에서는 누구나 볼 수
            있고, DRAFT 상태에서는 등록자·담당 교사·개발자에게만 실제 목록이 담기며 그 외
            요청자에게는 빈 배열이 반환된다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "조회 성공"),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(responseCode = "404", description = "프로그램이 없음 (PROGRAM_NOT_FOUND)"),
        SwaggerApiResponse(responseCode = "410", description = "삭제된 프로그램 (PROGRAM_DELETED)"),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @GetMapping("/{programId}")
    fun getProgram(
        authentication: Authentication,
        @Parameter(description = "조회할 프로그램 ID", example = "1") @PathVariable programId: Long,
    ): ApiResponse<ProgramDetailResponse> {
        val memberId = authentication.principal as Long
        return ApiResponse.of(programService.getDetail(programId, memberId))
    }

    @Operation(
        summary = "프로그램 신청·취소",
        description = """
            신청(APPLY)과 취소(CANCEL)를 하나의 Action API로 처리한다. 모든 프로그램은 선착순이며
            정원 확인·신청 저장·인원 반영을 하나의 Transaction과 Program Row Lock으로 처리해
            동시 요청에서도 정원을 초과하지 않는다. 재신청은 이력 보존형이다 — 취소해도 기존
            신청 Row는 남고, 다시 신청하면 새 Row가 생긴다(동일 학생·프로그램의 활성 신청은
            항상 최대 1건). 취소는 신청 종료 전까지만 가능하다.

            `answerData`의 Form Field 기준 값 검증(필수값 누락, 선택지 범위)과 `fileIds` 소유권
            검증은 이번 범위에 포함되지 않는다 — 값은 그대로 저장·왕복만 된다(Job Application과
            동일한 범위 결정).
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "신청·취소 성공"),
        SwaggerApiResponse(
            responseCode = "400",
            description =
                "재학 중이 아님(NOT_ENROLLED), 대상 학년이 아님(NOT_TARGET_GRADE), " +
                    "신청 기간이 아님(PROGRAM_NOT_OPEN), 신청 마감(PROGRAM_CLOSED)",
        ),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(responseCode = "403", description = "학생 권한이 없음 (FORBIDDEN)"),
        SwaggerApiResponse(
            responseCode = "404",
            description = "프로그램이 없음(PROGRAM_NOT_FOUND), 취소할 활성 신청이 없음(ACTIVE_APPLICATION_NOT_FOUND)",
        ),
        SwaggerApiResponse(
            responseCode = "409",
            description =
                "정원 마감(PROGRAM_FULL), 이미 신청함(ALREADY_APPLIED), " +
                    "신청 종료 후 취소 시도(PROGRAM_ACTION_NOT_AVAILABLE)",
        ),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @PostMapping("/{programId}/application-actions")
    fun executeApplicationAction(
        authentication: Authentication,
        @Parameter(description = "대상 프로그램 ID", example = "1") @PathVariable programId: Long,
        @Valid @RequestBody request: ProgramApplicationActionRequest,
    ): ApiResponse<ProgramApplicationActionResponse> {
        val studentMemberId = authentication.principal as Long
        return ApiResponse.of(programService.executeApplicationAction(programId, studentMemberId, request))
    }
}
