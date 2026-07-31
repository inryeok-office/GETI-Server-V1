package team.inreok.getiserver.domain.member.controller

import org.springframework.data.domain.Pageable
import org.springframework.http.HttpHeaders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import team.inreok.getiserver.domain.member.dto.MemberProfileResponse
import team.inreok.getiserver.domain.member.dto.MemberSearchResponse
import team.inreok.getiserver.domain.member.entity.type.AcademicStatus
import team.inreok.getiserver.domain.member.entity.type.DepartmentType
import team.inreok.getiserver.domain.member.service.MemberSearchService
import team.inreok.getiserver.domain.member.service.MemberService
import team.inreok.getiserver.global.web.ApiResponse
import team.inreok.getiserver.global.web.AuthorizationHeaderSupport

@RestController
@RequestMapping("/api/v1/members")
class MemberController(
    private val memberService: MemberService,
    private val memberSearchService: MemberSearchService,
) {
    // 요청자가 STUDENT Role인지(NOT_A_STUDENT 403)는 인증 주체를 알 수 없어 이번 구현에서 판단하지 않는다.
    @GetMapping("/{memberId}")
    fun getProfile(
        @RequestHeader(HttpHeaders.AUTHORIZATION) authorization: String,
        @PathVariable memberId: Long,
    ): ApiResponse<MemberProfileResponse> {
        AuthorizationHeaderSupport.require(authorization)
        return ApiResponse.of(memberService.getProfile(memberId))
    }

    @GetMapping
    fun searchMembers(
        @RequestHeader(HttpHeaders.AUTHORIZATION) authorization: String,
        @RequestParam(required = false) name: String?,
        @RequestParam(required = false) academicStatus: AcademicStatus?,
        @RequestParam(required = false) cohort: Int?,
        @RequestParam(required = false) department: DepartmentType?,
        @RequestParam(required = false) majorId: Long?,
        @RequestParam(required = false) techStackId: Long?,
        pageable: Pageable,
    ): ApiResponse<MemberSearchResponse> {
        AuthorizationHeaderSupport.require(authorization)
        return ApiResponse.of(
            memberSearchService.search(name, academicStatus, cohort, department, majorId, techStackId, pageable),
        )
    }
}
