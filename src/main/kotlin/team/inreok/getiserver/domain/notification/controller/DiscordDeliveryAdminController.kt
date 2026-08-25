package team.inreok.getiserver.domain.notification.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Pageable
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import team.inreok.getiserver.domain.notification.dto.DiscordDeliveryListResponse
import team.inreok.getiserver.domain.notification.dto.DiscordDeliveryStatusResponse
import team.inreok.getiserver.domain.notification.entity.type.DiscordDeliveryStatus
import team.inreok.getiserver.domain.notification.entity.type.DiscordDeliveryTargetType
import team.inreok.getiserver.domain.notification.exception.DiscordDeliveryManageForbiddenException
import team.inreok.getiserver.domain.notification.exception.DiscordDeliveryNotFoundForTargetException
import team.inreok.getiserver.domain.notification.service.DiscordDeliveryAdminQueryService
import team.inreok.getiserver.domain.notification.service.DiscordDeliveryService
import team.inreok.getiserver.domain.program.query.ProgramManagerQueryPort
import team.inreok.getiserver.global.openapi.BEARER_AUTH_SCHEME
import team.inreok.getiserver.global.web.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

/**
 * Job/Program/Inquiry의 Discord 전달 상태를 조회하고, Program은 수동 재시도까지 지원한다
 * (Notification 요구사항 §36·§37, Issue #97). 대상 종류를 가리지 않는 횡단 목록 조회도 함께
 * 제공한다(Issue #206).
 *
 * 대상별 조회 URL은 각 도메인의 기존 admin 경로 아래에 있지만 Controller는 `domain.notification`에
 * 둔다 -- `domain.job`/`domain.program`에 두면 그 Module이 `domain.notification`을 참조하게 되는데,
 * `notification`이 이미 `job.query`/`program.query`를 소비하고 있어 반대 방향 의존이 곧바로
 * 순환이 된다(`discord-event-wiring-plan.md` §3 결정 7). `SecurityConfig`의 기존
 * `/api/v1/admin/{jobs,programs,inquiries}` 하위 전체에 적용되는 Role 규칙이 그 경로들에는 그대로
 * 미치므로 별도 선언이 필요 없다.
 *
 * 반면 횡단 목록(`/api/v1/admin/discord-deliveries`)은 `notification` 자신의 admin 경로라 기존
 * Role 규칙이 닿지 않는다 -- `SecurityConfig`에 DEVELOPER 전용 규칙을 직접 선언했다. 세 종류를 한
 * Table에 섞어 보여주므로 그중 가장 엄격한 Inquiry 규칙에 맞춘 것이다
 * (`discord-delivery-admin-list-plan.md` 결정 1).
 *
 * Inquiry는 조회만 제공한다 -- 수동 재시도 API는 Notification 요구사항 §37에 명시되지 않았다.
 */
@Tag(
    name = "Notification - Discord 전달 상태",
    description = "공고·프로그램·문의의 Discord 전달 상태를 조회·목록 조회하고, 프로그램은 실패한 전달을 다시 시도한다.",
)
@SecurityRequirement(name = BEARER_AUTH_SCHEME)
@RestController
class DiscordDeliveryAdminController(
    private val discordDeliveryService: DiscordDeliveryService,
    private val discordDeliveryAdminQueryService: DiscordDeliveryAdminQueryService,
    private val programManagerQueryPort: ProgramManagerQueryPort,
) {
    @Operation(
        summary = "Discord 전달 내역 전체 목록 조회",
        description = """
            대상 종류(JOB/PROGRAM/INQUIRY)를 가리지 않고 최근 Discord 전달 내역을 최신순으로
            조회한다. 개발자만 접근할 수 있다 -- 세 종류를 한 목록에 섞어 보여주므로 그중 가장
            엄격한 문의 관리 권한 기준을 따른다. 기본 page=0, size=20이며 최대 size=100이다.
            정렬은 최신순으로 고정이라 `sort` 파라미터는 무시한다.

            한 대상에 여러 전달이 있을 수 있다(예: 공고 게시 후 수정 시 CREATE·UPDATE 두 건).
            **재시도는 이 API가 제공하지 않고 대상별 기존 Endpoint**(`POST /api/v1/admin/jobs/
            {jobId}/discord/retry` 등)**를 사용한다.** 그 Endpoint는 대상의 가장 최근 전달만 다시
            시도하므로, 항목의 `canRetry`가 false면 재시도를 요청해도 409로 거절된다 -- 재시도
            버튼은 `canRetry=true`인 항목에만 노출해야 한다.

            `targetName`은 저장된 값이 아니라 조회 시점에 원본 리소스에서 다시 읽은 값이다. 공고와
            프로그램은 제목이고, 문의는 개인정보 최소화 원칙에 따라 제목 대신 문의 유형이 담긴다.
            같은 이유로 메시지 본문은 제공하지 않는다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "조회 성공(결과가 없으면 빈 목록)"),
        SwaggerApiResponse(responseCode = "400", description = "status 값이 올바르지 않음 (TYPE_MISMATCH)"),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(responseCode = "403", description = "개발자 권한이 없음 (FORBIDDEN)"),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @GetMapping("/api/v1/admin/discord-deliveries")
    fun listDiscordDeliveries(
        @Parameter(description = "전달 상태 Filter(선택). 생략하면 전체를 조회한다.")
        @RequestParam(required = false)
        status: DiscordDeliveryStatus?,
        @Parameter(description = "Pagination(page: 0부터 시작, size: 기본 20, 최대 100). sort는 무시된다.")
        pageable: Pageable,
    ): ApiResponse<DiscordDeliveryListResponse> =
        ApiResponse.of(discordDeliveryAdminQueryService.listRecent(status, pageable))

    @Operation(
        summary = "공고 Discord 전달 상태 조회",
        description = """
            공고 게시·수정·마감·삭제에 대응하는 Discord Delivery의 현재 상태를 조회한다. 교사 또는
            개발자가 접근할 수 있다 -- 공고는 담당자 기준이 아직 확정되지 않아 이 API도 다른 공고
            관리 API와 동일하게 역할만 검증한다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "조회 성공"),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(responseCode = "403", description = "교사 또는 개발자 권한이 없음 (FORBIDDEN)"),
        SwaggerApiResponse(
            responseCode = "404",
            description = "이 공고로 예약된 Discord 전달이 없음 (DISCORD_DELIVERY_NOT_FOUND)",
        ),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @GetMapping("/api/v1/admin/jobs/{jobId}/discord")
    fun getJobDiscordStatus(
        @Parameter(description = "조회할 공고 ID", example = "1") @PathVariable jobId: Long,
    ): ApiResponse<DiscordDeliveryStatusResponse> =
        ApiResponse.of(discordDeliveryService.findStatus(DiscordDeliveryTargetType.JOB, jobId))

    @Operation(
        summary = "공고 Discord 전달 수동 재시도",
        description = """
            FAILED 상태인 공고 Discord Delivery를 다시 시도 대기 상태로 되돌린다. 새 Delivery를
            만들지 않고 같은 Row를 재사용하며, 자동 재시도 횟수는 초기화되지만 수동 재시도 횟수는
            누적된다(최대 3회). FAILED가 아니거나 수동 재시도 상한을 모두 썼으면 거부된다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "재시도 예약 성공"),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(responseCode = "403", description = "교사 또는 개발자 권한이 없음 (FORBIDDEN)"),
        SwaggerApiResponse(
            responseCode = "404",
            description = "이 공고로 예약된 Discord 전달이 없음 (DISCORD_DELIVERY_NOT_FOUND)",
        ),
        SwaggerApiResponse(
            responseCode = "409",
            description =
                "FAILED 상태가 아니어서 재시도할 수 없음(DISCORD_DELIVERY_NOT_RETRYABLE), " +
                    "수동 재시도 상한 소진(DISCORD_DELIVERY_RETRY_LIMIT_EXCEEDED)",
        ),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @PostMapping("/api/v1/admin/jobs/{jobId}/discord/retry")
    fun retryJobDiscord(
        @Parameter(description = "재시도할 공고 ID", example = "1") @PathVariable jobId: Long,
    ): ApiResponse<DiscordDeliveryStatusResponse> =
        ApiResponse.of(discordDeliveryService.retryManuallyForTarget(DiscordDeliveryTargetType.JOB, jobId))

    @Operation(
        summary = "프로그램 Discord 전달 상태 조회",
        description = """
            프로그램 게시·수정·삭제에 대응하는 Discord Delivery의 현재 상태를 조회한다. 등록자,
            담당 교사 또는 개발자만 접근할 수 있다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "조회 성공"),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(
            responseCode = "403",
            description = "교사·개발자가 아니거나 등록자·담당 교사가 아님 (FORBIDDEN, DISCORD_DELIVERY_MANAGE_FORBIDDEN)",
        ),
        SwaggerApiResponse(
            responseCode = "404",
            description = "프로그램이 없거나 이 프로그램으로 예약된 Discord 전달이 없음 (DISCORD_DELIVERY_NOT_FOUND)",
        ),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @GetMapping("/api/v1/admin/programs/{programId}/discord")
    fun getProgramDiscordStatus(
        authentication: Authentication,
        @Parameter(description = "조회할 프로그램 ID", example = "1") @PathVariable programId: Long,
    ): ApiResponse<DiscordDeliveryStatusResponse> {
        requireProgramManager(authentication, programId)
        return ApiResponse.of(discordDeliveryService.findStatus(DiscordDeliveryTargetType.PROGRAM, programId))
    }

    @Operation(
        summary = "프로그램 Discord 전달 수동 재시도",
        description = """
            FAILED 상태인 프로그램 Discord Delivery를 다시 시도 대기 상태로 되돌린다. 새 Delivery를
            만들지 않고 같은 Row를 재사용하며, 자동 재시도 횟수는 초기화되지만 수동 재시도 횟수는
            누적된다(최대 3회). 등록자, 담당 교사 또는 개발자만 요청할 수 있다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "재시도 예약 성공"),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(
            responseCode = "403",
            description = "교사·개발자가 아니거나 등록자·담당 교사가 아님 (FORBIDDEN, DISCORD_DELIVERY_MANAGE_FORBIDDEN)",
        ),
        SwaggerApiResponse(
            responseCode = "404",
            description = "프로그램이 없거나 이 프로그램으로 예약된 Discord 전달이 없음 (DISCORD_DELIVERY_NOT_FOUND)",
        ),
        SwaggerApiResponse(
            responseCode = "409",
            description =
                "FAILED 상태가 아니어서 재시도할 수 없음(DISCORD_DELIVERY_NOT_RETRYABLE), " +
                    "수동 재시도 상한 소진(DISCORD_DELIVERY_RETRY_LIMIT_EXCEEDED)",
        ),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @PostMapping("/api/v1/admin/programs/{programId}/discord/retry")
    fun retryProgramDiscord(
        authentication: Authentication,
        @Parameter(description = "재시도할 프로그램 ID", example = "1") @PathVariable programId: Long,
    ): ApiResponse<DiscordDeliveryStatusResponse> {
        requireProgramManager(authentication, programId)
        return ApiResponse.of(
            discordDeliveryService.retryManuallyForTarget(DiscordDeliveryTargetType.PROGRAM, programId),
        )
    }

    @Operation(
        summary = "문의 Discord 전달 상태 조회",
        description = """
            문의 접수 알림에 대응하는 Discord Delivery의 현재 상태를 조회한다. 개발자만 접근할 수
            있다(문의 관리 권한 Matrix와 동일). 수동 재시도 API는 제공하지 않는다 -- 요구사항에
            명시되지 않았다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "조회 성공"),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(responseCode = "403", description = "개발자 권한이 없음 (FORBIDDEN)"),
        SwaggerApiResponse(
            responseCode = "404",
            description = "이 문의로 예약된 Discord 전달이 없음 (DISCORD_DELIVERY_NOT_FOUND)",
        ),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @GetMapping("/api/v1/admin/inquiries/{inquiryId}/discord")
    fun getInquiryDiscordStatus(
        @Parameter(description = "조회할 문의 ID", example = "1") @PathVariable inquiryId: Long,
    ): ApiResponse<DiscordDeliveryStatusResponse> =
        ApiResponse.of(discordDeliveryService.findStatus(DiscordDeliveryTargetType.INQUIRY, inquiryId))

    /**
     * `ProgramServiceImpl.requireManager`와 같은 규칙(등록자 또는 담당 교사, 개발자는 우회)이다.
     * 그 Method 자체는 `program` Module 내부 구현이라 `notification`이 직접 호출할 수 없어
     * (Module 경계), 판정에 필요한 최소 정보만 [ProgramManagerQueryPort]로 공개받아 여기서 같은
     * 비교를 수행한다(`discord-event-wiring-plan.md` §7 결정 11). 판정 로직을 Port에 넣지 않은
     * 것은 접근 제어 결정을 소비 측이 명시적으로 갖도록 하기 위해서다 -- 규칙이 바뀌면 두 곳을
     * 함께 고쳐야 하므로, 규칙 변경 시 이 주석과 `requireManager`를 같이 확인한다.
     */
    private fun requireProgramManager(
        authentication: Authentication,
        programId: Long,
    ) {
        val isDeveloper = authentication.authorities.any { it.authority == "ROLE_DEVELOPER" }
        if (isDeveloper) return

        val memberId = authentication.principal as Long
        val manager =
            programManagerQueryPort.findById(programId)
                ?: throw DiscordDeliveryNotFoundForTargetException(DiscordDeliveryTargetType.PROGRAM, programId)
        val isManager = memberId == manager.createdByMemberId || memberId == manager.managerMemberId
        if (!isManager) throw DiscordDeliveryManageForbiddenException()
    }
}
