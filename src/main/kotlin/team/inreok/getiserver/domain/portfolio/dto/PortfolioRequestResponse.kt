package team.inreok.getiserver.domain.portfolio.dto

import io.swagger.v3.oas.annotations.media.Schema
import team.inreok.getiserver.domain.portfolio.entity.PortfolioRequest
import team.inreok.getiserver.domain.portfolio.entity.type.PortfolioRequestStatus
import java.time.LocalDateTime

/**
 * 포트폴리오 수합 요청 상세 응답이다(요구사항 §10). 공개 상세와 관리자 상세가 같은 구조를 쓴다.
 *
 * `files`(안내/양식 파일), `mySubmission`(요청자 학생의 제출), `submitted`(요청자 제출 완료 여부)는
 * 각각 File Integration(Phase 3)과 Student Submission(Phase 2)에서 추가한다 -- 가짜 값을 지금
 * 내보내면 Client가 없는 기능을 있는 것으로 오해하므로 Field 자체를 넣지 않았다. 상태 변경 응답의
 * `notificationCount`(§23)도 Notification 연동(Phase 5) 전까지 넣지 않는다.
 */
@Schema(description = "포트폴리오 수합 요청 상세 정보")
data class PortfolioRequestResponse(
    @param:Schema(description = "수합 요청 ID", example = "1")
    val requestId: Long,
    @param:Schema(description = "제목", example = "2026 상반기 포트폴리오 수합")
    val title: String,
    @param:Schema(description = "설명", example = "졸업 포트폴리오를 제출해 주세요.", nullable = true)
    val description: String?,
    @param:Schema(description = "상태", example = "PUBLISHED")
    val status: PortfolioRequestStatus,
    @param:Schema(description = "제출 마감 시각", example = "2026-09-30T23:59:59")
    val dueAt: LocalDateTime,
    @param:Schema(description = "제출 대상 학생 수", example = "20")
    val targetCount: Long,
    @param:Schema(description = "제출 완료(SUBMITTED) 학생 수. 임시저장(DRAFT)은 포함하지 않는다.", example = "5")
    val submittedCount: Long,
    @param:Schema(description = "생성 시각", example = "2026-08-20T09:00:00")
    val createdAt: LocalDateTime?,
    @param:Schema(description = "수정 시각", example = "2026-08-20T09:00:00")
    val updatedAt: LocalDateTime?,
) {
    companion object {
        fun from(
            request: PortfolioRequest,
            targetCount: Long,
            submittedCount: Long,
        ): PortfolioRequestResponse =
            PortfolioRequestResponse(
                requestId = requireNotNull(request.id) { "저장된 PortfolioRequest는 id를 가져야 합니다." },
                title = request.title,
                description = request.description,
                status = request.status,
                dueAt = request.dueAt,
                targetCount = targetCount,
                submittedCount = submittedCount,
                createdAt = request.createdAt,
                updatedAt = request.updatedAt,
            )
    }
}
