package team.inreok.getiserver.domain.member.controller

import org.springframework.data.domain.Pageable
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import team.inreok.getiserver.domain.member.dto.MemberProfileResponse
import team.inreok.getiserver.domain.member.dto.MemberSearchResponse
import team.inreok.getiserver.domain.member.entity.type.AcademicStatus
import team.inreok.getiserver.domain.member.entity.type.DepartmentType
import team.inreok.getiserver.domain.member.exception.NotAStudentException
import team.inreok.getiserver.domain.member.service.MemberSearchService
import team.inreok.getiserver.domain.member.service.MemberService
import team.inreok.getiserver.global.web.ApiResponse

// SecurityConfig가 이 경로를 인증 필수로 지정하므로, 여기 도달했다는 것은 이미 유효한
// Access Token(JwtAuthenticationFilter)이 SecurityContext에 인증 정보를 채웠다는 뜻이다.
@RestController
@RequestMapping("/api/v1/members")
class MemberController(
    private val memberService: MemberService,
    private val memberSearchService: MemberSearchService,
) {
    @GetMapping("/{memberId}")
    fun getProfile(
        authentication: Authentication,
        @PathVariable memberId: Long,
    ): ApiResponse<MemberProfileResponse> {
        requireStudentRequester(authentication)
        return ApiResponse.of(memberService.getProfile(memberId))
    }

    @GetMapping
    fun searchMembers(
        @RequestParam(required = false) name: String?,
        @RequestParam(required = false) academicStatus: AcademicStatus?,
        @RequestParam(required = false) cohort: Int?,
        @RequestParam(required = false) department: DepartmentType?,
        @RequestParam(required = false) majorId: Long?,
        @RequestParam(required = false) techStackId: Long?,
        pageable: Pageable,
    ): ApiResponse<MemberSearchResponse> =
        ApiResponse.of(
            memberSearchService.search(name, academicStatus, cohort, department, majorId, techStackId, pageable),
        )

    // Access Token의 roles Claim(JwtAuthenticationFilter가 ROLE_ 접두어를 붙여 채운 authorities)에
    // STUDENT가 없으면 거부한다(Issue #50 후속, 요청자 Role 검증).
    private fun requireStudentRequester(authentication: Authentication) {
        val isStudent = authentication.authorities.any { it.authority == "ROLE_STUDENT" }
        if (!isStudent) throw NotAStudentException()
    }
}
