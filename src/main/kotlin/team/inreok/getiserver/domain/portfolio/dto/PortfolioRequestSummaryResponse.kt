package team.inreok.getiserver.domain.portfolio.dto

import io.swagger.v3.oas.annotations.media.Schema
import team.inreok.getiserver.domain.portfolio.entity.PortfolioRequest
import team.inreok.getiserver.domain.portfolio.entity.type.PortfolioRequestStatus
import java.time.LocalDateTime

/**
 * 포트폴리오 수합 요청 목록 항목 응답이다(요구사항 §9).
 *
 * 요청자 학생의 제출 완료 여부(`submitted`)는 Student Submission(Phase 2)에서 추가한다 -- 제출
 * 개념이 아직 없어 지금은 넣지 않는다. [targetCount]/[submittedCount]는 목록 항목마다 단건
 * 조회하면 N+1이 되므로 호출 측(Service)이 이번 Page 전체를 한 번에 집계한 값을 전달한다(§35).
 */
@Schema(description = "포트폴리오 수합 요청 목록 항목")
data class PortfolioRequestSummaryResponse(
    @param:Schema(description = "수합 요청 ID", example = "1")
    val requestId: Long,
    @param:Schema(description = "제목", example = "2026 상반기 포트폴리오 수합")
    val title: String,
    @param:Schema(description = "상태", example = "PUBLISHED")
    val status: PortfolioRequestStatus,
    @param:Schema(description = "제출 마감 시각", example = "2026-09-30T23:59:59")
    val dueAt: LocalDateTime,
    @param:Schema(description = "제출 대상 학생 수", example = "20")
    val targetCount: Long,
    @param:Schema(description = "제출 완료(SUBMITTED) 학생 수", example = "5")
    val submittedCount: Long,
) {
    companion object {
        fun from(
            request: PortfolioRequest,
            targetCount: Long,
            submittedCount: Long,
        ): PortfolioRequestSummaryResponse =
            PortfolioRequestSummaryResponse(
                requestId = requireNotNull(request.id) { "저장된 PortfolioRequest는 id를 가져야 합니다." },
                title = request.title,
                status = request.status,
                dueAt = request.dueAt,
                targetCount = targetCount,
                submittedCount = submittedCount,
            )
    }
}
