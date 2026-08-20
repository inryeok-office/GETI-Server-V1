package team.inreok.getiserver.domain.portfolio.exception

import team.inreok.getiserver.global.error.BusinessException

class PortfolioRequestNotFoundException(
    requestId: Long,
) : BusinessException(
        PortfolioErrorCode.PORTFOLIO_REQUEST_NOT_FOUND,
        "포트폴리오 수합 요청(id=$requestId)을 찾을 수 없습니다.",
    )
