package team.inreok.getiserver.domain.recommendation.exception

import team.inreok.getiserver.global.error.BusinessException

/** 관심 없음 해제 대상이 없거나(존재하지 않음) 요청자 본인 소유가 아닐 때 발생한다. 두 경우를
 * 구분하지 않고 같은 응답을 준다(소유권 존재 여부 비노출, Notion 계약에 403이 없다). */
class RecommendationExclusionNotFoundException(
    exclusionId: Long,
) : BusinessException(RecommendationErrorCode.EXCLUSION_NOT_FOUND, "관심 없음 설정(id=$exclusionId)을 찾을 수 없습니다.")
