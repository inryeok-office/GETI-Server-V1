package team.inreok.getiserver.domain.portfolio.exception

import team.inreok.getiserver.global.error.BusinessException

/** 학생이 자신이 대상이 아닌 수합 요청을 조회·제출하려 할 때 사용한다(요구사항 §2/§10). */
class NotRequestTargetException : BusinessException(PortfolioErrorCode.NOT_REQUEST_TARGET)
