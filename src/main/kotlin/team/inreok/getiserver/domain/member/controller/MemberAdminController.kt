package team.inreok.getiserver.domain.member.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import team.inreok.getiserver.domain.member.dto.AdminMemberDetailResponse
import team.inreok.getiserver.domain.member.dto.AdminMemberRoleUpdateRequest
import team.inreok.getiserver.domain.member.dto.AdminMemberSearchResponse
import team.inreok.getiserver.domain.member.dto.AdminMemberStatusUpdateRequest
import team.inreok.getiserver.domain.member.dto.MemberApprovalRequest
import team.inreok.getiserver.domain.member.dto.MemberApprovalResponse
import team.inreok.getiserver.domain.member.entity.type.DepartmentType
import team.inreok.getiserver.domain.member.entity.type.MemberStatus
import team.inreok.getiserver.domain.member.entity.type.RoleType
import team.inreok.getiserver.domain.member.service.MemberAdminManagementService
import team.inreok.getiserver.domain.member.service.MemberApprovalService
import team.inreok.getiserver.global.openapi.BEARER_AUTH_SCHEME
import team.inreok.getiserver.global.web.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

@Tag(
    name = "Member - 관리자 회원 관리",
    description =
        "Google OAuth로 가입해 승인 대기(PENDING) 중인 교직원을 승인·거절하고(Issue #99), Role/계정 " +
            "상태를 가리지 않고 회원을 검색·조회하며 Role Set·계정 상태를 변경한다(Issue #183). 필요 권한: 개발자.",
)
@SecurityRequirement(name = BEARER_AUTH_SCHEME)
// SecurityConfig가 이 경로를 DEVELOPER 권한 필수로 지정하므로, 여기 도달했다는 것은 이미 유효한
// Access Token으로 인증되고 DEVELOPER 역할을 가졌다는 뜻이다(Issue #99, #183 -- 이 Controller의
// 모든 Endpoint가 DEVELOPER 전용이다. 담당자 선택 Dropdown용 목록 조회(TEACHER도 접근)는 같은
// 경로 GET /api/v1/admin/members를 쓰는 별도 AdminMemberQueryController(Issue #182)가 맡는다 --
// 새 목록 조회는 그 Endpoint와 겹치지 않도록 GET /api/v1/admin/members/search로 분리했다).
@RestController
@RequestMapping("/api/v1/admin/members")
class MemberAdminController(
    private val memberApprovalService: MemberApprovalService,
    private val memberAdminManagementService: MemberAdminManagementService,
) {
    @Operation(
        summary = "교직원 가입 승인·거절",
        description = """
            승인 대기(PENDING) 중인 Google OAuth 교직원을 승인하거나 거절한다.

            - APPROVE: 회원 상태를 ACTIVE로 바꾸고 TEACHER Role을 부여한다. 승인 이후 해당 회원이
              새로 로그인하거나 Refresh Token을 사용하면 Access Token의 roles에 TEACHER가 반영된다
              (Token은 재발급 시 최신 Role을 다시 조회한다).
            - REJECT: 회원 상태를 REJECTED로 바꾸고 거절 사유(reason)를 저장한다. reason은 필수다.

            승인/거절은 PENDING 상태의 교직원(GOOGLE)에 대해서만 허용한다. 그 외 상태는 409, 교직원
            승인 대상이 아닌 Provider(예: DG 학생)는 400으로 거부한다. 같은 회원에 대한 동시 승인/거절은
            회원 Row Lock으로 순차 처리되어 하나의 상태 전이만 성공한다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "승인·거절 처리 성공"),
        SwaggerApiResponse(
            responseCode = "400",
            description =
                "거절인데 사유(reason) 누락(MEMBER_REJECTION_REASON_REQUIRED), 교직원 승인 대상이 아닌 " +
                    "Provider(MEMBER_NOT_APPROVAL_TARGET), 요청 값 형식 오류(VALIDATION_FAILED), " +
                    "필수 Field 누락·잘못된 action 값(MALFORMED_JSON)",
        ),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(responseCode = "403", description = "개발자 권한이 없음 (FORBIDDEN)"),
        SwaggerApiResponse(responseCode = "404", description = "회원이 존재하지 않음 (MEMBER_NOT_FOUND)"),
        SwaggerApiResponse(responseCode = "409", description = "회원이 승인 대기(PENDING) 상태가 아님 (MEMBER_NOT_PENDING)"),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @PostMapping("/{memberId}/approval-actions")
    fun processApproval(
        @Parameter(description = "승인·거절할 회원 ID", example = "42") @PathVariable memberId: Long,
        @Valid @RequestBody request: MemberApprovalRequest,
    ): ApiResponse<MemberApprovalResponse> = ApiResponse.of(memberApprovalService.process(memberId, request))

    @Operation(
        summary = "관리자용 회원 목록 조회",
        description = """
            Role/계정 상태를 가리지 않고 모든 회원을 검색·조회한다(Issue #183). 승인 대기(PENDING)
            중인 교직원도 이 목록에 포함되므로, 개별 memberId를 미리 몰라도 교직원 가입 승인 대상을
            확인할 수 있다. name/status/role/cohort/department 모두 선택이며, 지정한 Filter만
            AND로 결합해 적용한다. 기본 page=0, size=20이며 id 역순(최신 가입순)으로 고정된다.

            이 Endpoint는 담당자 선택 Dropdown용 GET /api/v1/admin/members(Issue #182, TEACHER도
            접근 가능, role 필터만, Pagination 없음)와는 별개다 -- 이 Endpoint는 DEVELOPER 전용이고
            email 등 더 넓은 정보를 담는다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "조회 성공(결과가 없으면 빈 목록)"),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(responseCode = "403", description = "개발자 권한이 없음 (FORBIDDEN)"),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @GetMapping("/search")
    fun search(
        @Parameter(description = "회원 이름 부분 검색(선택, 대소문자 무시)") @RequestParam(required = false) name: String?,
        @Parameter(description = "계정 상태 필터(선택)") @RequestParam(required = false) status: MemberStatus?,
        @Parameter(description = "Role 필터(선택)") @RequestParam(required = false) role: RoleType?,
        @Parameter(description = "기수 필터(선택)") @RequestParam(required = false) cohort: Int?,
        @Parameter(description = "학과 필터(선택)") @RequestParam(required = false) department: DepartmentType?,
        @Parameter(description = "Pagination(page: 0부터 시작, size: 기본 20)") pageable: Pageable,
    ): ApiResponse<AdminMemberSearchResponse> =
        ApiResponse.of(memberAdminManagementService.search(name, status, role, cohort, department, pageable))

    @Operation(
        summary = "회원 상세 조회(관리자 관점)",
        description = "회원 1건의 상세 정보를 조회한다(Issue #183). 관리자 전용 Endpoint라 email·GitHub URL·전화번호를 포함한다.",
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "조회 성공"),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(responseCode = "403", description = "개발자 권한이 없음 (FORBIDDEN)"),
        SwaggerApiResponse(responseCode = "404", description = "회원이 존재하지 않음 (MEMBER_NOT_FOUND)"),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @GetMapping("/{memberId}")
    fun getDetail(
        @Parameter(description = "조회할 회원 ID", example = "42") @PathVariable memberId: Long,
    ): ApiResponse<AdminMemberDetailResponse> = ApiResponse.of(memberAdminManagementService.getDetail(memberId))

    @Operation(
        summary = "회원 Role Set 교체",
        description = """
            이 회원이 최종적으로 가질 Role 집합으로 전체를 교체한다(Issue #183 -- 개별 추가/제거가
            아니라 요청한 집합으로 통째로 바꾼다. 기존에 있던 Role 중 요청에 없으면 회수되고, 없던
            Role 중 요청에 있으면 새로 부여된다). 자기 자신(요청자 본인)의 Role은 스스로 바꿀 수
            없다 -- 위험한 권한 상실 사고를 막기 위해 대상이 본인이면 항상 403으로 거부한다. 같은
            회원에 대한 동시 요청은 회원 Row Lock으로 순차 처리된다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "Role 교체 성공"),
        SwaggerApiResponse(responseCode = "400", description = "요청 값 형식 오류(VALIDATION_FAILED, MALFORMED_JSON)"),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(
            responseCode = "403",
            description = "개발자 권한이 없음(FORBIDDEN), 자기 자신의 Role을 스스로 바꾸려 함(MEMBER_SELF_MODIFICATION_FORBIDDEN)",
        ),
        SwaggerApiResponse(responseCode = "404", description = "회원이 존재하지 않음 (MEMBER_NOT_FOUND)"),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @PatchMapping("/{memberId}/roles")
    fun updateRoles(
        authentication: Authentication,
        @Parameter(description = "Role을 변경할 회원 ID", example = "42") @PathVariable memberId: Long,
        @Valid @RequestBody request: AdminMemberRoleUpdateRequest,
    ): ApiResponse<AdminMemberDetailResponse> {
        val requesterMemberId = authentication.principal as Long
        return ApiResponse.of(
            memberAdminManagementService.updateRoles(memberId, requesterMemberId, request.roles),
        )
    }

    @Operation(
        summary = "회원 계정 상태 변경",
        description = """
            회원의 계정 상태를 변경한다(Issue #183). 관리자가 수동으로 전이할 수 있는 것은
            ACTIVE <-> SUSPENDED뿐이다 -- PENDING/REJECTED/WITHDRAWN 관련 전이는 각각 전용
            Flow(교직원 승인·거절, 회원 탈퇴)의 책임이라 이 API로는 다루지 않으며, 그 외 조합은
            모두 409로 거부한다. 자기 자신(요청자 본인)의 계정 상태는 스스로 바꿀 수 없다 --
            위험한 계정 잠금 사고를 막기 위해 대상이 본인이면 항상 403으로 거부한다. 같은 회원에
            대한 동시 요청은 회원 Row Lock으로 순차 처리되어 하나만 성공한다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "상태 변경 성공"),
        SwaggerApiResponse(responseCode = "400", description = "요청 값 형식 오류(VALIDATION_FAILED, MALFORMED_JSON)"),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(
            responseCode = "403",
            description = "개발자 권한이 없음(FORBIDDEN), 자기 자신의 상태를 스스로 바꾸려 함(MEMBER_SELF_MODIFICATION_FORBIDDEN)",
        ),
        SwaggerApiResponse(responseCode = "404", description = "회원이 존재하지 않음 (MEMBER_NOT_FOUND)"),
        SwaggerApiResponse(
            responseCode = "409",
            description = "허용되지 않는 상태 전이 (MEMBER_STATUS_TRANSITION_NOT_ALLOWED)",
        ),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @PatchMapping("/{memberId}/status")
    fun updateStatus(
        authentication: Authentication,
        @Parameter(description = "상태를 변경할 회원 ID", example = "42") @PathVariable memberId: Long,
        @Valid @RequestBody request: AdminMemberStatusUpdateRequest,
    ): ApiResponse<AdminMemberDetailResponse> {
        val requesterMemberId = authentication.principal as Long
        return ApiResponse.of(
            memberAdminManagementService.updateStatus(memberId, requesterMemberId, request.status),
        )
    }
}
