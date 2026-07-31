package team.inreok.getiserver.domain.member.controller

import org.springframework.http.HttpHeaders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import team.inreok.getiserver.domain.member.dto.MajorListResponse
import team.inreok.getiserver.domain.member.service.MajorService
import team.inreok.getiserver.global.web.ApiResponse
import team.inreok.getiserver.global.web.AuthorizationHeaderSupport

@RestController
@RequestMapping("/api/v1/metadata/majors")
class MajorController(
    private val majorService: MajorService,
) {
    @GetMapping
    fun getMajors(
        @RequestHeader(HttpHeaders.AUTHORIZATION) authorization: String,
        @RequestParam(required = false) activeOnly: Boolean?,
    ): ApiResponse<MajorListResponse> {
        AuthorizationHeaderSupport.require(authorization)
        return ApiResponse.of(majorService.search(activeOnly))
    }
}
