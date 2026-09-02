package team.inreok.getiserver.domain.portfolio.exception

import team.inreok.getiserver.domain.portfolio.entity.type.PortfolioRequestStatus
import team.inreok.getiserver.global.error.BusinessException

class PortfolioStatusTransitionInvalidException(
    current: PortfolioRequestStatus,
    target: PortfolioRequestStatus,
) : BusinessException(
        PortfolioErrorCode.PORTFOLIO_STATUS_TRANSITION_INVALID,
        "수합 요청 상태를 $current 에서 $target (으)로 변경할 수 없습니다.",
    )
