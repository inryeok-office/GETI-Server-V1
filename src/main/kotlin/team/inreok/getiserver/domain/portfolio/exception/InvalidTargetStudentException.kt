package team.inreok.getiserver.domain.portfolio.exception

import team.inreok.getiserver.global.error.BusinessException

/**
 * 대상으로 지정한 id 중 존재하지 않거나 STUDENT가 아닌 회원이 있을 때 사용한다(요구사항 §26).
 * 어떤 id가 문제인지는 응답에 노출하지 않는다(존재 여부 노출 방지, 안전한 문구만 사용).
 */
class InvalidTargetStudentException : BusinessException(PortfolioErrorCode.INVALID_TARGET_STUDENT)
