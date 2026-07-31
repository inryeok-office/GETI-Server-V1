package team.inreok.getiserver.domain.member.controller

import org.springframework.http.HttpHeaders
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import team.inreok.getiserver.domain.member.dto.MajorIdsRequest
import team.inreok.getiserver.domain.member.dto.MemberMajorsResponse
import team.inreok.getiserver.domain.member.service.MemberMajorService
import team.inreok.getiserver.global.web.ApiResponse
import team.inreok.getiserver.global.web.AuthorizationHeaderSupport

@RestController
@RequestMapping("/api/v1/me/majors")
class MemberMajorController(
    private val memberMajorService: MemberMajorService,
) {
    // memberId는 인증 시스템이 없어 Token에서 추출할 수 없는 동안의 임시 대체값이며,
    // 인증 구현 이후 이 Query Parameter를 제거하고 Authorization Token에서 추출하도록 바꿔야 한다.
    @PatchMapping
    fun replaceMyMajors(
        @RequestHeader(HttpHeaders.AUTHORIZATION) authorization: String,
        @RequestParam memberId: Long,
        @RequestBody request: MajorIdsRequest,
    ): ApiResponse<MemberMajorsResponse> {
        AuthorizationHeaderSupport.require(authorization)
        return ApiResponse.of(memberMajorService.replaceAll(memberId, request.majorIds))
    }
}
