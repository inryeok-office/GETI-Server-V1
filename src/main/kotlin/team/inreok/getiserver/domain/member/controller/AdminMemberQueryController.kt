package team.inreok.getiserver.domain.member.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import team.inreok.getiserver.domain.member.dto.AdminMemberListResponse
import team.inreok.getiserver.domain.member.entity.type.RoleType
import team.inreok.getiserver.domain.member.service.AdminMemberQueryService
import team.inreok.getiserver.global.openapi.BEARER_AUTH_SCHEME
import team.inreok.getiserver.global.web.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

@Tag(
    name = "Member - 관리자 회원 목록 조회",
    description = "관리자 화면의 담당자 선택 Dropdown 등을 위해 특정 Role의 회원 목록을 조회한다. 필요 권한: 교사, 개발자.",
)
@SecurityRequirement(name = BEARER_AUTH_SCHEME)
// SecurityConfig가 이 GET 경로(/api/v1/admin/members)를 TEACHER 또는 DEVELOPER 권한 필수로 지정하므로
// (Issue #182 — 지원자 관리 화면에서 담당 교사를 고른다), 여기 도달했다는 것은 이미 유효한 Access
// Token으로 인증되고 두 역할 중 하나를 가졌다는 뜻이다. 같은 경로의 POST 교직원 승인은 DEVELOPER 전용이다.
@RestController
@RequestMapping("/api/v1/admin/members")
class AdminMemberQueryController(
    private val adminMemberQueryService: AdminMemberQueryService,
) {
    @Operation(
        summary = "Role별 회원 목록 조회",
        description = """
            지정한 Role을 가진 ACTIVE 회원을 이름 오름차순으로 조회한다. 담당 교사 선택 Dropdown 등에서
            사용하며, 응답은 memberId와 name만 담아 email/phone 같은 민감 정보를 노출하지 않는다.

            담당자 선택 용도라 Pagination과 이름 검색은 두지 않고 전체를 돌려준다(교사 수가 학교 규모).
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "조회 성공(결과가 없으면 빈 목록)"),
        SwaggerApiResponse(
            responseCode = "400",
            description = "role 누락(MISSING_REQUEST_PARAMETER) 또는 형식 오류(TYPE_MISMATCH)",
        ),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(responseCode = "403", description = "교사 또는 개발자 권한이 없음 (FORBIDDEN)"),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @GetMapping
    fun listMembers(
        @Parameter(description = "조회할 회원 Role(담당자 Dropdown은 TEACHER)", example = "TEACHER")
        @RequestParam role: RoleType,
    ): ApiResponse<AdminMemberListResponse> = ApiResponse.of(adminMemberQueryService.listActiveMembersByRole(role))
}
