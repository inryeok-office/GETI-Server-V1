package team.inreok.getiserver.domain.program.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import team.inreok.getiserver.domain.program.dto.ProgramCreateRequest
import team.inreok.getiserver.domain.program.dto.ProgramCreateResponse
import team.inreok.getiserver.domain.program.dto.ProgramStatusUpdateRequest
import team.inreok.getiserver.domain.program.dto.ProgramStatusUpdateResponse
import team.inreok.getiserver.domain.program.dto.ProgramUpdateRequest
import team.inreok.getiserver.domain.program.dto.ProgramUpdateResponse
import team.inreok.getiserver.domain.program.service.ProgramService
import team.inreok.getiserver.global.openapi.BEARER_AUTH_SCHEME
import team.inreok.getiserver.global.web.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

@Tag(
    name = "Program - 프로그램 관리",
    description = "특강·교육 프로그램을 등록·수정하고 상태를 변경한다. 필요 권한: 교사, 개발자.",
)
@SecurityRequirement(name = BEARER_AUTH_SCHEME)
// SecurityConfig가 이 경로를 TEACHER 또는 DEVELOPER 역할 필수로 지정하므로, 여기 도달했다는 것은
// 이미 유효한 Access Token으로 인증되고 두 역할 중 하나를 가졌다는 뜻이다. 등록자·담당 교사 본인
// 검증(PROGRAM_MANAGE_FORBIDDEN)은 Role만으로는 알 수 없어 ProgramService가 별도로 수행하고,
// 개발자는 이 검증만 우회한다(JobApplicationFormLinkServiceImpl과 동일한 원칙).
@RestController
@RequestMapping("/api/v1/admin/programs")
class ProgramAdminController(
    private val programService: ProgramService,
) {
    @Operation(
        summary = "프로그램 등록·임시저장",
        description = """
            프로그램을 등록한다. `status`에 DRAFT를 지정하면 임시저장으로 저장되고, PUBLISHED를
            지정하면 게시 필수값(본문·장소·행사 일정·신청 일정·대상 학년·Discord 채널)을 모두
            검증한다. PUBLISHED로 등록하면 등록자가 단일 담당 교사로 지정된다.

            `formId`를 지정하면 본인 소유의 FormType=PROGRAM, FormStatus=ACTIVE 양식만 연결할 수
            있다. 본문 첨부파일(fileIds)은 이번 범위에 포함되지 않는다(File 도메인에 공개 Use
            Case가 아직 없음). `discordChannelId`는 학교 Discord 서버의 허용 채널 중 하나여야
            하며, 허용 목록에 없으면 즉시 거부된다. Discord 전달 상태는 이 응답에 포함되지 않고
            `GET /api/v1/admin/programs/{programId}/discord`로 별도 조회한다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "201", description = "등록 성공"),
        SwaggerApiResponse(
            responseCode = "400",
            description =
                "일정 역전(INVALID_PROGRAM_PERIOD), 잘못된 정원(INVALID_CAPACITY), " +
                    "대상 학년 값 오류(INVALID_TARGET_GRADE), 게시 시 대상 학년 누락(TARGET_GRADE_REQUIRED), " +
                    "게시 시 Discord 채널 누락(DISCORD_CHANNEL_REQUIRED), " +
                    "허용되지 않은 Discord 채널(DISCORD_CHANNEL_NOT_ALLOWED), " +
                    "연결할 수 없는 양식(PROGRAM_FORM_NOT_LINKABLE), " +
                    "그 외 게시 필수값 누락(PROGRAM_VALIDATION_FAILED), 요청 값 형식 오류(VALIDATION_FAILED)",
        ),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(responseCode = "403", description = "교사 또는 개발자 권한이 없음 (FORBIDDEN)"),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createProgram(
        authentication: Authentication,
        @Valid @RequestBody request: ProgramCreateRequest,
    ): ApiResponse<ProgramCreateResponse> {
        val memberId = authentication.principal as Long
        return ApiResponse.of(programService.create(request, memberId))
    }

    @Operation(
        summary = "프로그램 부분 수정",
        description = """
            요청 Body에 전달한 Field만 수정한다(부분 수정, PATCH). 전달하지 않았거나 null인 Field는
            기존 값을 유지한다. 게시 후 변경할 수 없는 프로그램 유형·대상 학년은 이 요청에 포함되지
            않는다. 정원 증가는 항상 허용되고 감소는 현재 활성 신청 인원 이상까지만 허용된다.
            등록자 또는 담당 교사만 수정할 수 있다(개발자는 이 검증을 우회한다).
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "수정 성공"),
        SwaggerApiResponse(
            responseCode = "400",
            description =
                "일정 역전(INVALID_PROGRAM_PERIOD), 연결할 수 없는 양식(PROGRAM_FORM_NOT_LINKABLE), " +
                    "게시 필수값 미충족(PROGRAM_VALIDATION_FAILED 등), 요청 값 형식 오류(VALIDATION_FAILED)",
        ),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(
            responseCode = "403",
            description = "교사·개발자가 아니거나 등록자·담당 교사가 아님 (FORBIDDEN, PROGRAM_MANAGE_FORBIDDEN)",
        ),
        SwaggerApiResponse(responseCode = "404", description = "프로그램이 없거나 삭제됨 (PROGRAM_NOT_FOUND)"),
        SwaggerApiResponse(
            responseCode = "409",
            description = "정원을 현재 활성 신청 인원보다 적게 설정 (CAPACITY_BELOW_CURRENT_APPLICANTS)",
        ),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @PatchMapping("/{programId}")
    fun updateProgram(
        authentication: Authentication,
        @Parameter(description = "수정할 프로그램 ID", example = "1") @PathVariable programId: Long,
        @Valid @RequestBody request: ProgramUpdateRequest,
    ): ApiResponse<ProgramUpdateResponse> {
        val memberId = authentication.principal as Long
        val isDeveloper = authentication.authorities.any { it.authority == "ROLE_DEVELOPER" }
        return ApiResponse.of(programService.update(programId, memberId, isDeveloper, request))
    }

    @Operation(
        summary = "프로그램 상태 변경",
        description = """
            프로그램을 게시하거나 삭제한다. 허용되는 상태 전이는 다음과 같고 그 외(마감된
            프로그램 재게시, 삭제된 프로그램 복구, 조기 마감 포함)는 409로 거부한다.

            ```
            DRAFT     -> PUBLISHED | DELETED
            PUBLISHED -> DELETED
            CLOSED    -> DELETED
            ```

            `PUBLISHED -> CLOSED`는 이 API로 지정할 수 없다 — 신청 종료 시각 도달 시 서버
            Scheduler(`ProgramCloseScheduler`)가 자동으로 처리한다. DELETED는 Soft Delete로 처리해
            실제 행을 지우지 않으므로 기존 신청·이력이 보존된다. 등록자 또는 담당 교사만 변경할
            수 있다(개발자는 이 검증을 우회한다).
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "상태 변경 성공"),
        SwaggerApiResponse(responseCode = "400", description = "게시 필수값 미충족"),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(
            responseCode = "403",
            description = "교사·개발자가 아니거나 등록자·담당 교사가 아님 (FORBIDDEN, PROGRAM_MANAGE_FORBIDDEN)",
        ),
        SwaggerApiResponse(responseCode = "404", description = "프로그램이 없거나 삭제됨 (PROGRAM_NOT_FOUND)"),
        SwaggerApiResponse(
            responseCode = "409",
            description =
                "허용되지 않은 상태 전이(PROGRAM_STATUS_TRANSITION_NOT_ALLOWED), " +
                    "마감된 프로그램 재게시(PROGRAM_REOPEN_NOT_ALLOWED)",
        ),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @PatchMapping("/{programId}/status")
    fun changeProgramStatus(
        authentication: Authentication,
        @Parameter(description = "상태를 변경할 프로그램 ID", example = "1") @PathVariable programId: Long,
        @Valid @RequestBody request: ProgramStatusUpdateRequest,
    ): ApiResponse<ProgramStatusUpdateResponse> {
        val memberId = authentication.principal as Long
        val isDeveloper = authentication.authorities.any { it.authority == "ROLE_DEVELOPER" }
        return ApiResponse.of(programService.changeStatus(programId, memberId, isDeveloper, request))
    }
}
