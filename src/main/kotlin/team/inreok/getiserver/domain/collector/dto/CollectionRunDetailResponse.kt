package team.inreok.getiserver.domain.collector.dto

import io.swagger.v3.oas.annotations.media.Schema
import team.inreok.getiserver.domain.collector.entity.CollectionRun
import team.inreok.getiserver.domain.collector.entity.CollectionRunError
import team.inreok.getiserver.domain.collector.entity.type.CollectionRunStatus
import java.time.LocalDateTime

@Schema(description = "수집 실행 오류 상세")
data class CollectionRunErrorResponse(
    @param:Schema(description = "실패한 외부 공고 ID(nullable, Provider 전체 실패면 null)", nullable = true)
    val externalJobId: String?,
    @param:Schema(description = "오류 코드")
    val code: String,
    @param:Schema(description = "오류 설명(Secret·원문 응답 미포함)")
    val message: String,
    @param:Schema(description = "누락된 정규화 필드 이름 목록")
    val missingFields: List<String>,
    @param:Schema(description = "발생 시각")
    val occurredAt: LocalDateTime,
) {
    companion object {
        fun from(error: CollectionRunError): CollectionRunErrorResponse =
            CollectionRunErrorResponse(
                externalJobId = error.externalJobId,
                code = error.code,
                message = error.message,
                missingFields =
                    error.missingFields
                        ?.split(",")
                        ?.map { it.trim() }
                        ?.filter { it.isNotEmpty() } ?: emptyList(),
                occurredAt = error.occurredAt,
            )
    }
}

@Schema(description = "수집 실행 상세")
data class CollectionRunDetailResponse(
    @param:Schema(description = "수집 실행 ID")
    val runId: Long,
    @param:Schema(description = "수집원 ID")
    val sourceId: Long,
    @param:Schema(description = "수집원 이름")
    val sourceName: String,
    @param:Schema(description = "실행 방식 내부 식별자(CollectorAction 확정 전까지 내부 값)")
    val action: String,
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
    @param:Schema(description = "종료 시각(nullable)", nullable = true)
    val finishedAt: LocalDateTime?,
    @param:Schema(description = "전체 처리 건수")
    val totalCount: Long,
    @param:Schema(description = "오류 목록")
    val errors: List<CollectionRunErrorResponse>,
) {
    companion object {
        fun from(
            run: CollectionRun,
            sourceName: String,
            errors: List<CollectionRunError>,
        ): CollectionRunDetailResponse =
            CollectionRunDetailResponse(
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
                totalCount = run.totalCount.toLong(),
                errors = errors.map(CollectionRunErrorResponse::from),
            )
    }
}
