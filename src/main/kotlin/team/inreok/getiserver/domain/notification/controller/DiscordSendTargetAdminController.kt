package team.inreok.getiserver.domain.notification.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Pageable
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import team.inreok.getiserver.domain.notification.dto.DiscordSendTargetListResponse
import team.inreok.getiserver.domain.notification.entity.type.DiscordSendTargetType
import team.inreok.getiserver.domain.notification.service.DiscordSendTargetQueryService
import team.inreok.getiserver.global.openapi.BEARER_AUTH_SCHEME
import team.inreok.getiserver.global.web.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

@Tag(
    name = "Notification - Discord 수동 발송 대상",
    description = "PUBLISHED Job/Program의 최초 수동 Discord 발송 후보를 조회한다.",
)
@SecurityRequirement(name = BEARER_AUTH_SCHEME)
@RestController
class DiscordSendTargetAdminController(
    private val queryService: DiscordSendTargetQueryService,
) {
    @Operation(
        summary = "Discord 수동 발송 대상 목록 조회",
        description = """
            Discord 전달 이력 목록이 아니라 PUBLISHED Job/Program 중 수동 최초 발송 가능한 대상을
            포함한 Discord 발송 대상 조회 API다. 아직 CREATE Discord Delivery가 없는 대상은
            `delivery=null`로 반환하고, CREATE Delivery가 있으면 최신 CREATE Delivery만 반환한다.
            Inquiry, DRAFT, CLOSED, DELETED Resource는 포함하지 않는다. 정렬은 createdAt DESC,
            id DESC, targetType ASC로 고정하며 page는 0부터 시작한다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "조회 성공(결과가 없으면 빈 Page)"),
        SwaggerApiResponse(
            responseCode = "400",
            description = "targetGrade가 1~3이 아니거나 지원하지 않는 targetType, 허용 범위를 초과한 page",
        ),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(responseCode = "403", description = "개발자 권한이 없음 (FORBIDDEN)"),
    )
    @GetMapping("/api/v1/admin/discord-send-targets")
    fun listDiscordSendTargets(
        @Parameter(description = "대상 종류 Filter(선택). 생략하면 JOB과 PROGRAM을 함께 조회한다.", example = "JOB")
        @RequestParam(required = false)
        targetType: DiscordSendTargetType?,
        @Parameter(description = "Job/Program title 부분 검색. 앞뒤 공백은 제거한다.", example = "백엔드")
        @RequestParam(required = false)
        targetName: String?,
        @Parameter(description = "대상 학년 Filter(선택). 1, 2, 3 중 하나이며 미지정 학년 대상은 전체 학년으로 간주한다.", example = "2")
        @RequestParam(required = false)
        targetGrade: Int?,
        @Parameter(description = "Pagination(page: 0부터 시작, 최대 100, size: 기본 20, 최대 100). sort는 무시된다.")
        pageable: Pageable,
    ): ApiResponse<DiscordSendTargetListResponse> =
        ApiResponse.of(queryService.list(targetType, targetName, targetGrade, pageable))
}
