package team.inreok.getiserver.domain.recommendation.exception

import team.inreok.getiserver.global.error.BusinessException

/**
 * 관심 없음/북마크 등록이 같은 (memberId, jobId)에 새 `MemberJobPreference` Row를 만들려는 다른
 * 요청과 경합해 `uk_member_job_preferences_member_job` UNIQUE 제약을 위반했을 때 발생한다(코드리뷰
 * 반영, PR #178). 관심 없음과 북마크가 같은 제약을 공유하는 두 번째 Insert 경로가 생기면서, 이
 * 경합에서 진 요청 입장에서는 상대가 관심 없음 등록이었는지 북마크 등록이었는지 알 수 없다 -- 그래서
 * `RecommendationExclusionAlreadyExistsException`/`RecommendationBookmarkAlreadyExistsException`로
 * 단정하지 않는다.
 */
class RecommendationPreferenceWriteConflictException(
    memberId: Long,
    jobId: Long,
) : BusinessException(
        RecommendationErrorCode.PREFERENCE_WRITE_CONFLICT,
        "회원(id=$memberId) 공고(id=$jobId) 설정이 동시 요청과 충돌했습니다.",
    )
