package team.inreok.getiserver.domain.portfolio.exception

import team.inreok.getiserver.domain.portfolio.entity.type.PortfolioRequestStatus
import team.inreok.getiserver.global.error.BusinessException

/**
 * 현재 상태에서 허용되지 않는 수정을 시도할 때 사용한다(요구사항 §8). CLOSED/DELETED 요청은
 * 어떤 Field도 수정할 수 없고, 대상 학생 교체는 DRAFT에서만 허용한다(§27의 고아 Submission
 * 문제를 피하기 위한 Phase 1 기본값).
 */
class PortfolioRequestNotEditableException(
    status: PortfolioRequestStatus,
    reason: String = "현재 상태($status)에서는 수합 요청을 수정할 수 없습니다.",
) : BusinessException(PortfolioErrorCode.PORTFOLIO_REQUEST_NOT_EDITABLE, reason)
