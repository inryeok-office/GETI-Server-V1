package team.inreok.getiserver.global.web

import org.slf4j.MDC
import org.springframework.modulith.NamedInterface

// Domain Module의 Controller가 공통 성공 응답으로 사용해야 하므로(docs/development/web-api.md
// "Controller 원칙" 참고) global Module 밖으로 명시적으로 공개한다.
@NamedInterface
data class ApiResponse<T>(
    val success: Boolean = true,
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
data class ResponseMeta(
    val requestId: String? = MDC.get(RequestIdFilter.REQUEST_ID_MDC_KEY),
)
