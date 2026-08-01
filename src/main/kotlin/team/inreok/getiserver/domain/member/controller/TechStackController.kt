package team.inreok.getiserver.domain.member.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpHeaders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import team.inreok.getiserver.domain.member.dto.TechStackListResponse
import team.inreok.getiserver.domain.member.entity.type.TechStackCategory
import team.inreok.getiserver.domain.member.service.TechStackService
import team.inreok.getiserver.global.openapi.BEARER_AUTH_SCHEME
import team.inreok.getiserver.global.web.ApiResponse
import team.inreok.getiserver.global.web.AuthorizationHeaderSupport
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

@Tag(name = "Member - 메타데이터", description = "전공/기술 스택 등 선택 가능한 메타데이터 목록을 조회한다. 필요 권한: 학생, 교사, 개발자.")
@SecurityRequirement(name = BEARER_AUTH_SCHEME)
@RestController
@RequestMapping("/api/v1/metadata/tech-stacks")
class TechStackController(
    private val techStackService: TechStackService,
) {
    @Operation(
        summary = "기술 스택 목록 조회",
        description = """
            선택 가능한 기술 스택 메타데이터 목록을 이름순으로 조회한다. query로 이름 부분 검색,
            category로 분류 필터링을 할 수 있다. 이 Endpoint는 아직 SecurityConfig의 JWT 검증에
            연동되지 않아 Authorization Header가 비어 있지 않은지만 확인한다(형식/서명 검증 없음, 후속 작업).
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "조회 성공"),
        SwaggerApiResponse(responseCode = "400", description = "Authorization Header가 비어 있음 (INVALID_REQUEST)"),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @GetMapping
    fun getTechStacks(
        @Parameter(description = "Bearer Access Token", example = "Bearer eyJhbGciOi...", `in` = ParameterIn.HEADER)
        @RequestHeader(HttpHeaders.AUTHORIZATION)
        authorization: String,
        @Parameter(description = "기술 스택 이름 부분 검색어", example = "Kotlin")
        @RequestParam(required = false)
        query: String?,
        @Parameter(description = "기술 스택 분류 필터")
        @RequestParam(required = false)
        category: TechStackCategory?,
    ): ApiResponse<TechStackListResponse> {
        AuthorizationHeaderSupport.require(authorization)
        return ApiResponse.of(techStackService.search(query, category))
    }
}
