package team.inreok.getiserver.domain.member.controller

import org.springframework.http.HttpHeaders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import team.inreok.getiserver.domain.member.dto.TechStackListResponse
import team.inreok.getiserver.domain.member.entity.type.TechStackCategory
import team.inreok.getiserver.domain.member.service.TechStackService
import team.inreok.getiserver.global.web.ApiResponse
import team.inreok.getiserver.global.web.AuthorizationHeaderSupport

@RestController
@RequestMapping("/api/v1/metadata/tech-stacks")
class TechStackController(
    private val techStackService: TechStackService,
) {
    @GetMapping
    fun getTechStacks(
        @RequestHeader(HttpHeaders.AUTHORIZATION) authorization: String,
        @RequestParam(required = false) query: String?,
        @RequestParam(required = false) category: TechStackCategory?,
    ): ApiResponse<TechStackListResponse> {
        AuthorizationHeaderSupport.require(authorization)
        return ApiResponse.of(techStackService.search(query, category))
    }
}
