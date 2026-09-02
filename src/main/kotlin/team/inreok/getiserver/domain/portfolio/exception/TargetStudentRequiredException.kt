package team.inreok.getiserver.domain.portfolio.exception

import team.inreok.getiserver.global.error.BusinessException

/** 대상 학생이 한 명도 없을 때 사용한다(요구사항 §4/§26 "Request 공개 전 Target 최소 1명 보장"). */
class TargetStudentRequiredException : BusinessException(PortfolioErrorCode.TARGET_STUDENT_REQUIRED)
