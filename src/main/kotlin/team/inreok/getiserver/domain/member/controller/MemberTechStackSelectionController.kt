package team.inreok.getiserver.domain.member.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import team.inreok.getiserver.domain.member.dto.MemberTechStacksResponse
import team.inreok.getiserver.domain.member.dto.TechStackIdsRequest
import team.inreok.getiserver.domain.member.exception.NotAStudentException
import team.inreok.getiserver.domain.member.service.MemberTechStackSelectionService
import team.inreok.getiserver.global.openapi.BEARER_AUTH_SCHEME
import team.inreok.getiserver.global.web.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

@Tag(name = "Member - 전공/기술스택", description = "로그인한 본인의 전공과 기술스택을 전체 교체한다. 학생 전용 API다.")
@SecurityRequirement(name = BEARER_AUTH_SCHEME)
// SecurityConfig가 이 경로를 인증 필수로 지정하므로, 여기 도달했다는 것은 이미 유효한
// Access Token(JwtAuthenticationFilter)이 SecurityContext에 인증 정보를 채웠다는 뜻이다.
@RestController
@RequestMapping("/api/v1/me/tech-stacks")
class MemberTechStackSelectionController(
    private val memberTechStackSelectionService: MemberTechStackSelectionService,
) {
    // 기술 스택은 학생 전용 개념이라 교사/개발자는 설정할 수 없다(코드 리뷰 Major 반영, PR #45부터
    // 있던 Gap을 요청자 Role을 알 수 있게 된 이번 PR에서 해소).
    @Operation(
        summary = "내 기술 스택 전체 교체",
        description = """
            요청 Body의 techStackIds로 내 기술 스택 목록을 통째로 교체한다(기존 선택은 모두 삭제 후
            새로 저장). 빈 배열을 보내면 선택이 모두 해제된다. 중복 ID는 조용히 제거되고 정상
            처리된다(내 전공 교체 API와 달리 중복 거부 Error Code가 없음). 필요 권한: 학생.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "교체 성공, 교체된 기술 스택 목록을 이름순으로 반환"),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(responseCode = "403", description = "학생이 아닌 회원의 요청 (NOT_A_STUDENT)"),
        SwaggerApiResponse(responseCode = "404", description = "존재하지 않는 techStackId 포함 (TECH_STACK_NOT_FOUND)"),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @PatchMapping
    fun replaceMyTechStacks(
        authentication: Authentication,
        @RequestBody request: TechStackIdsRequest,
    ): ApiResponse<MemberTechStacksResponse> {
        requireStudentRequester(authentication)
        val memberId = authentication.principal as Long
        return ApiResponse.of(memberTechStackSelectionService.replaceAll(memberId, request.techStackIds))
    }

    private fun requireStudentRequester(authentication: Authentication) {
        val isStudent = authentication.authorities.any { it.authority == "ROLE_STUDENT" }
        if (!isStudent) throw NotAStudentException()
    }
}
