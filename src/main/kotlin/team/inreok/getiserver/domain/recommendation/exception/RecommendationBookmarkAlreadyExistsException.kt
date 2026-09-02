package team.inreok.getiserver.domain.recommendation.exception

import team.inreok.getiserver.global.error.BusinessException

/** 이미 북마크한 공고에 다시 북마크 등록을 요청했을 때 발생한다(Issue #170). */
class RecommendationBookmarkAlreadyExistsException(
    jobId: Long,
) : BusinessException(RecommendationErrorCode.BOOKMARK_ALREADY_EXISTS, "공고(id=$jobId)는 이미 북마크되어 있습니다.")
