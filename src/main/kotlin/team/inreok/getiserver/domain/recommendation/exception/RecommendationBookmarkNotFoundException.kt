package team.inreok.getiserver.domain.recommendation.exception

import team.inreok.getiserver.global.error.BusinessException

/** 북마크 해제 대상이 없을 때(Row 자체가 없거나 `bookmarked=false`) 발생한다(Issue #170). 다른
 * 사용자 소유 여부를 노출하지 않는다 -- `jobId`는 항상 요청자 본인 기준으로만 조회한다. */
class RecommendationBookmarkNotFoundException(
    jobId: Long,
) : BusinessException(RecommendationErrorCode.BOOKMARK_NOT_FOUND, "공고(id=$jobId)를 북마크하지 않았습니다.")
