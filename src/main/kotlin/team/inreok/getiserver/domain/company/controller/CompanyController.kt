package team.inreok.getiserver.domain.company.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Pageable
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import team.inreok.getiserver.domain.company.dto.CompanyResponse
import team.inreok.getiserver.domain.company.dto.CompanySearchResponse
import team.inreok.getiserver.domain.company.entity.type.CompanyType
import team.inreok.getiserver.domain.company.entity.type.MouStatus
import team.inreok.getiserver.domain.company.service.CompanyService
import team.inreok.getiserver.global.openapi.BEARER_AUTH_SCHEME
import team.inreok.getiserver.global.web.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

@Tag(
    name = "Company - 기업 조회",
    description = "기업 목록을 검색하고 상세 정보를 조회한다. 필요 권한: 학생, 교사, 개발자.",
)
@SecurityRequirement(name = BEARER_AUTH_SCHEME)
// SecurityConfig가 이 경로를 인증 필수로 지정하므로, 여기 도달했다는 것은 이미 유효한
// Access Token(JwtAuthenticationFilter)이 SecurityContext에 인증 정보를 채웠다는 뜻이다.
@RestController
@RequestMapping("/api/v1/companies")
class CompanyController(
    private val companyService: CompanyService,
) {
    @Operation(
        summary = "기업 검색·목록 조회",
        description = """
            등록된 기업을 검색한다. `query`는 기업명 부분 일치(대소문자 무시)이며 생략하면 전체를
            조회한다. `companyType`, `mouStatus`, `sourceName`으로 추가 필터링할 수 있다. 삭제된
            기업은 결과에서 제외된다. 목록 기본값 page=0, size=20이며 최대 size=100이다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "조회 성공(결과가 없으면 빈 목록)"),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @GetMapping
    fun searchCompanies(
        authentication: Authentication,
        @Parameter(description = "기업명 검색어(부분 일치, 선택). 생략하면 전체 조회", example = "인력")
        @RequestParam(required = false)
        query: String?,
        @Parameter(description = "기업 유형 필터(선택)")
        @RequestParam(required = false)
        companyType: CompanyType?,
        @Parameter(description = "MOU 협약 상태 필터(선택)")
        @RequestParam(required = false)
        mouStatus: MouStatus?,
        @Parameter(description = "정보 출처 필터(선택, 완전 일치)", example = "manual")
        @RequestParam(required = false)
        sourceName: String?,
        @Parameter(description = "Pagination(page: 0부터 시작, size: 기본 20, 최대 100)")
        pageable: Pageable,
    ): ApiResponse<CompanySearchResponse> =
        ApiResponse.of(
            companyService.search(
                authentication.principal as Long,
                query,
                companyType,
                mouStatus,
                sourceName,
                pageable,
            ),
        )

    @Operation(
        summary = "기업 상세 조회",
        description = """
            companyId로 지정한 기업의 상세 정보를 조회한다. 삭제된 기업은 404로 처리한다.
            `logoUrl`은 로고가 등록되어 있으면 브라우저가 바로 표시할 수 있는 짧은 유효기간의
            서명된 URL이고, 없으면 null이다. 연결된 공개 공고 목록은 Job 도메인 구현 후 별도
            작업에서 추가된다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "조회 성공"),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(responseCode = "404", description = "기업이 없거나 삭제됨 (COMPANY_NOT_FOUND)"),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @GetMapping("/{companyId}")
    fun getCompany(
        authentication: Authentication,
        @Parameter(description = "조회할 기업 ID", example = "1") @PathVariable companyId: Long,
    ): ApiResponse<CompanyResponse> = ApiResponse.of(companyService.get(companyId, authentication.principal as Long))
}
