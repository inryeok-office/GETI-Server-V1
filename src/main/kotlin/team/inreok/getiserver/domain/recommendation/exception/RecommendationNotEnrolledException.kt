package team.inreok.getiserver.domain.recommendation.exception

import team.inreok.getiserver.global.error.BusinessException

/** 재학 중(`AcademicStatus.ENROLLED`)이 아닌 회원이 추천 설정을 변경하려 할 때 발생한다(Notion
 * 기능명세 "졸업생에게는 추천 설정을 제공하지 않는다"). */
class RecommendationNotEnrolledException : BusinessException(RecommendationErrorCode.NOT_ENROLLED)
