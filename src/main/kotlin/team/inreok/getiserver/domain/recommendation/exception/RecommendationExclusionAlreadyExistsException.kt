package team.inreok.getiserver.domain.recommendation.exception

import team.inreok.getiserver.global.error.BusinessException

/** 같은 공고에 이미 관심 없음이 설정돼 있는데 다시 등록을 요청했을 때 발생한다. */
class RecommendationExclusionAlreadyExistsException(
    jobId: Long,
) : BusinessException(RecommendationErrorCode.EXCLUSION_ALREADY_EXISTS, "공고(id=$jobId)는 이미 관심 없음으로 설정되어 있습니다.")
