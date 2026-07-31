package team.inreok.getiserver.domain.member.controller

import org.springframework.http.HttpHeaders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import team.inreok.getiserver.domain.member.dto.MemberProfileUpdateResponse
import team.inreok.getiserver.domain.member.dto.MyProfileResponse
import team.inreok.getiserver.domain.member.service.MemberService
import team.inreok.getiserver.global.web.ApiResponse
import team.inreok.getiserver.global.web.AuthorizationHeaderSupport
import tools.jackson.databind.JsonNode

@RestController
@RequestMapping("/api/v1/me/profile")
class MemberProfileController(
    private val memberService: MemberService,
) {
    // memberId는 인증 시스템이 없어 Token에서 추출할 수 없는 동안의 임시 대체값이며,
    // 인증 구현 이후 이 Query Parameter를 제거하고 Authorization Token에서 추출하도록 바꿔야 한다.
    @GetMapping
    fun getMyProfile(
        @RequestHeader(HttpHeaders.AUTHORIZATION) authorization: String,
        @RequestParam memberId: Long,
    ): ApiResponse<MyProfileResponse> {
        AuthorizationHeaderSupport.require(authorization)
        return ApiResponse.of(memberService.getMyProfile(memberId))
    }

    @PatchMapping
    fun updateMyProfile(
        @RequestHeader(HttpHeaders.AUTHORIZATION) authorization: String,
        @RequestParam memberId: Long,
        @RequestBody body: JsonNode,
    ): ApiResponse<MemberProfileUpdateResponse> {
        AuthorizationHeaderSupport.require(authorization)
        return ApiResponse.of(memberService.updateProfile(memberId, body))
    }
}
