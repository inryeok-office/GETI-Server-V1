package team.inreok.getiserver.domain.member.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import team.inreok.getiserver.domain.member.dto.MemberApprovalRequest
import team.inreok.getiserver.domain.member.dto.MemberApprovalResponse
import team.inreok.getiserver.domain.member.service.MemberApprovalService
import team.inreok.getiserver.global.openapi.BEARER_AUTH_SCHEME
import team.inreok.getiserver.global.web.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

@Tag(
    name = "Member - 교직원 가입 승인",
    description = "Google OAuth로 가입해 승인 대기(PENDING) 중인 교직원을 승인하거나 거절한다. 필요 권한: 개발자.",
)
@SecurityRequirement(name = BEARER_AUTH_SCHEME)
// SecurityConfig가 이 경로를 DEVELOPER 권한 필수로 지정하므로, 여기 도달했다는 것은 이미 유효한
// Access Token으로 인증되고 DEVELOPER 역할을 가졌다는 뜻이다(Issue #99).
@RestController
@RequestMapping("/api/v1/admin/members")
class MemberAdminController(
    private val memberApprovalService: MemberApprovalService,
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
}
