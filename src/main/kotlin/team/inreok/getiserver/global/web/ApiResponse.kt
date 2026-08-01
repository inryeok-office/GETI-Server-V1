package team.inreok.getiserver.global.web

import io.swagger.v3.oas.annotations.media.Schema
import org.slf4j.MDC
import org.springframework.modulith.NamedInterface

// Domain Module의 Controller가 공통 성공 응답으로 사용해야 하므로(docs/development/web-api.md
// "Controller 원칙" 참고) global Module 밖으로 명시적으로 공개한다.
@NamedInterface
@Schema(description = "공통 성공 응답 Wrapper. data는 각 API가 실제로 반환하는 값이다.")
data class ApiResponse<T>(
    @param:Schema(description = "요청 성공 여부(성공 응답은 항상 true)", example = "true")
    val success: Boolean = true,
    @param:Schema(description = "실제 응답 데이터")
    val data: T,
    val meta: ResponseMeta = ResponseMeta(),
) {
    @NamedInterface
    companion object {
        fun <T> of(data: T): ApiResponse<T> = ApiResponse(data = data)
    }
}

// GETI Notion API 명세서의 success/data/meta.requestId Wrapper 구조를 따른다
// (docs/audit/notion-repository-sync.md DECISION_REQUIRED 반영). ErrorResponse도
// 같은 meta 구조를 사용하므로 global.web에 공통으로 둔다.
@Schema(description = "응답 메타 정보")
data class ResponseMeta(
    @param:Schema(description = "요청 추적용 ID. 클라이언트가 X-Request-Id Header로 보내면 그 값을 재사용하고, 없으면 서버가 생성한다.", nullable = true)
    val requestId: String? = MDC.get(RequestIdFilter.REQUEST_ID_MDC_KEY),
)
