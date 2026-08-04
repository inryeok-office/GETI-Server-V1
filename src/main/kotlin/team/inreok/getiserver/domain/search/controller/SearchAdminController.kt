package team.inreok.getiserver.domain.search.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import team.inreok.getiserver.domain.search.dto.SearchActionRequest
import team.inreok.getiserver.domain.search.dto.SearchActionResponse
import team.inreok.getiserver.domain.search.reindex.SearchReindexService
import team.inreok.getiserver.global.openapi.BEARER_AUTH_SCHEME
import team.inreok.getiserver.global.web.ApiResponse
import java.time.LocalDateTime
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

@Tag(
    name = "Search - 검색 운영",
    description = "검색 색인 재구축 등 검색 운영 명령을 실행한다. 필요 권한: 개발자.",
)
@SecurityRequirement(name = BEARER_AUTH_SCHEME)
// SecurityConfig가 이 경로를 DEVELOPER 권한 필수로 지정하므로, 여기 도달했다는 것은 이미 유효한
// Access Token으로 인증되고 DEVELOPER 역할을 가졌다는 뜻이다.
@RestController
@RequestMapping("/api/v1/admin")
class SearchAdminController(
    private val searchReindexService: SearchReindexService,
) {
    @Operation(
        summary = "검색 색인 운영 명령 실행",
        description = """
            검색 색인 운영 명령을 실행한다(Issue #69). 현재 `action`이 지원하는 유일한 값은
            `REINDEX`(전체 재색인)다.

            요청 Thread는 실제 재색인이 끝나기 전에 즉시 반환한다(202 Accepted). 새 Elasticsearch
            Index를 만들어 전체 공개 대상 공고를 채운 뒤 Alias를 전환하며, 실패하면 기존 검색
            Index를 그대로 유지한다(검색 공백 없음). 이미 재색인이 진행 중이면 409를 반환한다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "202", description = "접수 성공"),
        SwaggerApiResponse(responseCode = "400", description = "요청 값 형식 오류(VALIDATION_FAILED)"),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(responseCode = "403", description = "개발자 권한이 없음 (FORBIDDEN)"),
        SwaggerApiResponse(responseCode = "409", description = "이미 전체 재색인이 진행 중임 (REINDEX_ALREADY_RUNNING)"),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @PostMapping("/search-actions")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun triggerSearchAction(
        @Valid @RequestBody request: SearchActionRequest,
    ): ApiResponse<SearchActionResponse> {
        val acceptedAt = LocalDateTime.now()
        val run = searchReindexService.triggerReindex()
        return ApiResponse.of(
            SearchActionResponse(
                operationId = requireNotNull(run.id),
                action = requireNotNull(request.action),
                status = run.status,
                acceptedAt = acceptedAt,
            ),
        )
    }
}
