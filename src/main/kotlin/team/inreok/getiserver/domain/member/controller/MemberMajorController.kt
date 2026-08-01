package team.inreok.getiserver.domain.member.controller

import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import team.inreok.getiserver.domain.member.dto.MajorIdsRequest
import team.inreok.getiserver.domain.member.dto.MemberMajorsResponse
import team.inreok.getiserver.domain.member.service.MemberMajorService
import team.inreok.getiserver.global.web.ApiResponse

// SecurityConfig가 이 경로를 인증 필수로 지정하므로, 여기 도달했다는 것은 이미 유효한
// Access Token(JwtAuthenticationFilter)이 SecurityContext에 인증 정보를 채웠다는 뜻이다.
@RestController
@RequestMapping("/api/v1/me/majors")
class MemberMajorController(
    private val memberMajorService: MemberMajorService,
) {
    @PatchMapping
    fun replaceMyMajors(
        authentication: Authentication,
        @RequestBody request: MajorIdsRequest,
    ): ApiResponse<MemberMajorsResponse> {
        val memberId = authentication.principal as Long
        return ApiResponse.of(memberMajorService.replaceAll(memberId, request.majorIds))
    }
}
