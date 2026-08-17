package team.inreok.getiserver.domain.recommendation.exception

import team.inreok.getiserver.global.error.BusinessException

/** 관심 없음 설정/해제 대상 공고가 없거나 이미 삭제됐을 때 발생한다. */
class RecommendationJobNotFoundException(
    jobId: Long,
) : BusinessException(RecommendationErrorCode.JOB_NOT_FOUND, "공고(id=$jobId)를 찾을 수 없습니다.")
