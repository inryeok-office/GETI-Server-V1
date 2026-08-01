package team.inreok.getiserver.domain.member.controller

import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import team.inreok.getiserver.domain.member.dto.MemberTechStacksResponse
import team.inreok.getiserver.domain.member.dto.TechStackIdsRequest
import team.inreok.getiserver.domain.member.exception.NotAStudentException
import team.inreok.getiserver.domain.member.service.MemberTechStackSelectionService
import team.inreok.getiserver.global.web.ApiResponse

// SecurityConfig가 이 경로를 인증 필수로 지정하므로, 여기 도달했다는 것은 이미 유효한
// Access Token(JwtAuthenticationFilter)이 SecurityContext에 인증 정보를 채웠다는 뜻이다.
@RestController
@RequestMapping("/api/v1/me/tech-stacks")
class MemberTechStackSelectionController(
    private val memberTechStackSelectionService: MemberTechStackSelectionService,
) {
    // 기술 스택은 학생 전용 개념이라 교사/개발자는 설정할 수 없다(코드 리뷰 Major 반영, PR #45부터
    // 있던 Gap을 요청자 Role을 알 수 있게 된 이번 PR에서 해소).
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
