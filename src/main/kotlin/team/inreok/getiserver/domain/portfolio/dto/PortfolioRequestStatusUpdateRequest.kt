package team.inreok.getiserver.domain.portfolio.dto

import io.swagger.v3.oas.annotations.media.Schema
import team.inreok.getiserver.domain.portfolio.entity.type.PortfolioRequestStatus

/**
 * 수합 요청 상태 변경 요청이다(요구사항 §7). 허용 전이는 다음과 같고, 그 외(동일 상태로의 변경
 * 포함)는 `PORTFOLIO_STATUS_TRANSITION_INVALID`(409)로 거부한다.
 *
 * ```text
 * DRAFT     -> PUBLISHED | DELETED
 * PUBLISHED -> CLOSED    | DELETED
 * CLOSED    -> DELETED
 * ```
 *
 * DRAFT->PUBLISHED(공개)는 대상 학생이 최소 한 명 있어야 한다(없으면 TARGET_STUDENT_REQUIRED).
 * DELETED는 Soft Delete로 처리되어 실제 행이 보존된다.
 */
@Schema(description = "수합 요청 상태 변경 요청")
data class PortfolioRequestStatusUpdateRequest(
    @param:Schema(description = "변경할 상태", example = "PUBLISHED")
    val status: PortfolioRequestStatus,
)
