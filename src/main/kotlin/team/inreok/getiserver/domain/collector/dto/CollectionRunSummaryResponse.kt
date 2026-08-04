package team.inreok.getiserver.domain.collector.dto

import io.swagger.v3.oas.annotations.media.Schema
import team.inreok.getiserver.domain.collector.entity.CollectionRun
import team.inreok.getiserver.domain.collector.entity.type.CollectionRunStatus
import team.inreok.getiserver.domain.collector.entity.type.CollectorAction
import java.time.LocalDateTime

@Schema(description = "수집 실행 목록 항목")
data class CollectionRunSummaryResponse(
    @param:Schema(description = "수집 실행 ID")
    val runId: Long,
    @param:Schema(description = "수집원 ID")
    val sourceId: Long,
    @param:Schema(description = "수집원 이름")
    val sourceName: String,
    @param:Schema(description = "실행 방식")
    val action: CollectorAction,
    @param:Schema(description = "실행 상태")
    val status: CollectionRunStatus,
    @param:Schema(description = "성공 건수")
    val successCount: Long,
    @param:Schema(description = "실패 건수")
    val failureCount: Long,
    @param:Schema(description = "품질 경고 건수")
    val partialQualityCount: Long,
    @param:Schema(description = "시작 시각")
    val startedAt: LocalDateTime,
    @param:Schema(description = "종료 시각(nullable, 진행 중이면 null)", nullable = true)
    val finishedAt: LocalDateTime?,
) {
    companion object {
        fun from(
            run: CollectionRun,
            sourceName: String,
        ): CollectionRunSummaryResponse =
            CollectionRunSummaryResponse(
                runId = requireNotNull(run.id),
                sourceId = run.sourceId,
                sourceName = sourceName,
                action = run.action,
                status = run.status,
                successCount = run.successCount.toLong(),
                failureCount = run.failureCount.toLong(),
                partialQualityCount = run.partialQualityCount.toLong(),
                startedAt = run.startedAt,
                finishedAt = run.finishedAt,
            )
    }
}

@Schema(description = "수집 실행 목록. Spring Data Page 규약과 동일하게 page는 0부터 시작한다.")
data class CollectionRunListResponse(
    @param:Schema(description = "검색 결과 목록")
    val content: List<CollectionRunSummaryResponse>,
    @param:Schema(description = "현재 Page 번호(0부터 시작)")
    val page: Int,
    @param:Schema(description = "Page당 개수")
    val size: Int,
    @param:Schema(description = "전체 결과 개수")
    val totalElements: Long,
    @param:Schema(description = "전체 Page 수")
    val totalPages: Int,
    @param:Schema(description = "첫 Page 여부")
    val first: Boolean,
    @param:Schema(description = "마지막 Page 여부")
    val last: Boolean,
)
