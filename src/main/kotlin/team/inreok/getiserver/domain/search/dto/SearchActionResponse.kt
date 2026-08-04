package team.inreok.getiserver.domain.search.dto

import io.swagger.v3.oas.annotations.media.Schema
import team.inreok.getiserver.domain.search.entity.type.SearchReindexStatus
import java.time.LocalDateTime

@Schema(description = "검색 색인 운영 명령 접수 결과")
data class SearchActionResponse(
    @param:Schema(description = "생성된 재색인 실행 ID", example = "1")
    val operationId: Long,
    @param:Schema(description = "실행한 동작")
    val action: SearchAction,
    @param:Schema(description = "접수 상태")
    val status: SearchReindexStatus,
    @param:Schema(description = "접수 시각")
    val acceptedAt: LocalDateTime,
)
