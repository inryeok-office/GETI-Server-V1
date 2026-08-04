package team.inreok.getiserver.domain.collector.dto

import io.swagger.v3.oas.annotations.media.Schema
import team.inreok.getiserver.domain.collector.entity.type.CollectionRunStatus
import java.time.LocalDateTime

@Schema(description = "수동 수집·동기화 실행 접수 결과")
data class CollectorActionResponse(
    @param:Schema(description = "생성된 수집 실행 ID 목록")
    val runIds: List<Long>,
    @param:Schema(description = "접수 상태")
    val status: CollectionRunStatus,
    @param:Schema(description = "접수 시각")
    val acceptedAt: LocalDateTime,
)
