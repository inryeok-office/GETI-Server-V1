package team.inreok.getiserver.domain.recommendation.access

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.job.access.JobBookmarkAccessor
import team.inreok.getiserver.domain.recommendation.repository.MemberJobPreferenceRepository

/**
 * `job.access.JobBookmarkAccessor`(SPI)의 구현이다(Issue #171). 새 조회를 만들지 않고 이미 있는
 * [MemberJobPreferenceRepository.findBookmarkedJobIdsByMemberIdAndJobIdIn]을 그대로 감싼다 --
 * `RecommendationServiceImpl`이 `RecommendationJobResponse.bookmarked`를 계산할 때 쓰는 원본과
 * 같은 Source다.
 *
 * `job`/`search`가 이 Interface를 소유하지 않고 `recommendation`이 구현하는 이유는
 * [JobBookmarkAccessor]의 Class 주석 참고.
 */
@Component
class JobBookmarkAccessorImpl(
    private val memberJobPreferenceRepository: MemberJobPreferenceRepository,
) : JobBookmarkAccessor {
    @Transactional(readOnly = true)
    override fun findAllByJobIds(
        jobIds: Set<Long>,
        requesterMemberId: Long,
    ): Set<Long> {
        if (jobIds.isEmpty()) return emptySet()
        return memberJobPreferenceRepository
            .findBookmarkedJobIdsByMemberIdAndJobIdIn(requesterMemberId, jobIds)
            .toSet()
    }
}
