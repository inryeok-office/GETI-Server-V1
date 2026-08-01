package team.inreok.getiserver.domain.member.controller

import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import team.inreok.getiserver.domain.member.dto.MemberProfileUpdateResponse
import team.inreok.getiserver.domain.member.dto.MyProfileResponse
import team.inreok.getiserver.domain.member.service.MemberService
import team.inreok.getiserver.global.web.ApiResponse
import tools.jackson.databind.JsonNode

// SecurityConfig가 이 경로를 인증 필수로 지정하므로, 여기 도달했다는 것은 이미 유효한
// Access Token(JwtAuthenticationFilter)이 SecurityContext에 인증 정보를 채웠다는 뜻이다.
@RestController
@RequestMapping("/api/v1/me/profile")
class MemberProfileController(
    private val memberService: MemberService,
) {
    @GetMapping
    fun getMyProfile(authentication: Authentication): ApiResponse<MyProfileResponse> {
        val memberId = authentication.principal as Long
        return ApiResponse.of(memberService.getMyProfile(memberId))
    }

    @PatchMapping
    fun updateMyProfile(
        authentication: Authentication,
        @RequestBody body: JsonNode,
    ): ApiResponse<MemberProfileUpdateResponse> {
        val memberId = authentication.principal as Long
        return ApiResponse.of(memberService.updateProfile(memberId, body))
    }
}
