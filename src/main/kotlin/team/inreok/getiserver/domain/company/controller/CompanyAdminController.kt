package team.inreok.getiserver.domain.company.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import team.inreok.getiserver.domain.company.dto.CompanyCreateRequest
import team.inreok.getiserver.domain.company.dto.CompanyResponse
import team.inreok.getiserver.domain.company.dto.CompanyUpdateRequest
import team.inreok.getiserver.domain.company.service.CompanyService
import team.inreok.getiserver.global.openapi.BEARER_AUTH_SCHEME
import team.inreok.getiserver.global.web.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

@Tag(
    name = "Company - 기업 관리",
    description = "기업 정보와 MOU 협약 상태를 등록·수정·삭제한다. 필요 권한: 개발자.",
)
@SecurityRequirement(name = BEARER_AUTH_SCHEME)
// SecurityConfig가 이 경로를 DEVELOPER 권한 필수로 지정하므로, 여기 도달했다는 것은 이미 유효한
// Access Token으로 인증되고 DEVELOPER 역할을 가졌다는 뜻이다.
@RestController
@RequestMapping("/api/v1/admin/companies")
class CompanyAdminController(
    private val companyService: CompanyService,
) {
    @Operation(
        summary = "기업 등록",
        description = """
            기업 기본 정보와 MOU 협약 상태를 등록한다. 기업명과 기업 유형이 모두 같은 기업이 이미
            등록되어 있으면 409로 거부한다. MOU 시작일이 종료일보다 늦으면 400으로 거부한다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "201", description = "등록 성공"),
        SwaggerApiResponse(
            responseCode = "400",
            description =
                "기업명이 없거나 공백(COMPANY_NAME_REQUIRED), MOU 기간 역전(MOU_PERIOD_INVALID), " +
                    "요청 값 형식 오류(VALIDATION_FAILED)",
        ),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(responseCode = "403", description = "개발자 권한이 없음 (FORBIDDEN)"),
        SwaggerApiResponse(responseCode = "409", description = "이름과 유형이 같은 기업이 이미 존재 (DUPLICATE_COMPANY)"),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createCompany(
        @Valid @RequestBody request: CompanyCreateRequest,
    ): ApiResponse<CompanyResponse> = ApiResponse.of(companyService.create(request))

    @Operation(
        summary = "기업 부분 수정",
        description = """
            요청 Body에 전달한 Field만 수정한다(부분 수정, PATCH). 전달하지 않았거나 null인 Field는
            기존 값을 유지한다. 기업명이나 유형을 바꿀 때 같은 이름·유형의 다른 기업이 있으면 409로
            거부한다. 수정 결과 MOU 시작일이 종료일보다 늦어지면 400으로 거부한다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "수정 성공, 수정된 전체 Field를 반환"),
        SwaggerApiResponse(
            responseCode = "400",
            description =
                "기업명이 공백(COMPANY_NAME_REQUIRED), MOU 기간 역전(MOU_PERIOD_INVALID), " +
                    "요청 값 형식 오류(VALIDATION_FAILED)",
        ),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(responseCode = "403", description = "개발자 권한이 없음 (FORBIDDEN)"),
        SwaggerApiResponse(responseCode = "404", description = "기업이 없거나 삭제됨 (COMPANY_NOT_FOUND)"),
        SwaggerApiResponse(responseCode = "409", description = "이름과 유형이 같은 다른 기업이 이미 존재 (DUPLICATE_COMPANY)"),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @PatchMapping("/{companyId}")
    fun updateCompany(
        @Parameter(description = "수정할 기업 ID", example = "1") @PathVariable companyId: Long,
        @Valid @RequestBody request: CompanyUpdateRequest,
    ): ApiResponse<CompanyResponse> = ApiResponse.of(companyService.update(companyId, request))

    @Operation(
        summary = "기업 Soft Delete",
        description = """
            기업을 Soft Delete한다(`deleted_at` 기록). 기존 공고와 이력을 유지하기 위해 실제 행을
            삭제하지 않는다. 삭제된 기업은 이후 목록·상세 조회에서 제외된다. 이미 삭제된 기업은
            404로 처리한다. 연결된 공개 공고가 있을 때의 차단은 Job 도메인 연동 후 추가된다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "204", description = "삭제 성공(응답 본문 없음)"),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(responseCode = "403", description = "개발자 권한이 없음 (FORBIDDEN)"),
        SwaggerApiResponse(responseCode = "404", description = "기업이 없거나 이미 삭제됨 (COMPANY_NOT_FOUND)"),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @DeleteMapping("/{companyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteCompany(
        @Parameter(description = "삭제할 기업 ID", example = "1") @PathVariable companyId: Long,
    ) {
        companyService.delete(companyId)
    }
}
