package team.inreok.getiserver.domain.portfolio.service.impl

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.portfolio.query.PortfolioRequestNotificationTargetQueryPort
import team.inreok.getiserver.domain.portfolio.query.PortfolioRequestNotificationTargetSnapshot
import team.inreok.getiserver.domain.portfolio.repository.PortfolioRequestRepository

/**
 * 다른 Domain Module(notification)에 공개된 조회 계약([PortfolioRequestNotificationTargetQueryPort])의
 * 구현이다. [PortfolioRequestTargetQueryPortImpl]과 같은 이유로 `PortfolioRequestServiceImpl`에
 * 합치지 않고 분리한다.
 */
@Service
class PortfolioRequestNotificationTargetQueryPortImpl(
    private val requestRepository: PortfolioRequestRepository,
) : PortfolioRequestNotificationTargetQueryPort {
    @Transactional(readOnly = true)
    override fun findAllByIds(
        requestIds: Set<Long>,
        viewerMemberId: Long,
    ): Map<Long, PortfolioRequestNotificationTargetSnapshot> {
        if (requestIds.isEmpty()) return emptyMap()
        return requestRepository
            .findNotificationTargetsByIds(requestIds, viewerMemberId)
            .associate { row ->
                row.requestId to
                    PortfolioRequestNotificationTargetSnapshot(
                        requestId = row.requestId,
                        status = row.status.name,
                        deleted = row.deletedAt != null,
                        targetedToViewer = row.targetedToViewer,
                    )
            }
    }
}
