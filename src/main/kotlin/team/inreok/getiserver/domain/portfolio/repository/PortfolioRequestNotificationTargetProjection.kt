package team.inreok.getiserver.domain.portfolio.repository

import team.inreok.getiserver.domain.portfolio.entity.type.PortfolioRequestStatus
import java.time.LocalDateTime

/**
 * [PortfolioRequestRepository.findNotificationTargetsByIds]의 결과 Projection이다. 알림 이동 대상
 * 판정에 필요한 Column과 요청자 대상 여부만 담는다(Issue #332).
 */
interface PortfolioRequestNotificationTargetProjection {
    val requestId: Long
    val status: PortfolioRequestStatus
    val deletedAt: LocalDateTime?
    val targetedToViewer: Boolean
}
