package team.inreok.getiserver.domain.recommendation.service.impl

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.recommendation.query.JobBookmarkAudienceQueryPort
import team.inreok.getiserver.domain.recommendation.repository.MemberJobPreferenceRepository

/** [JobBookmarkAudienceQueryPort]의 구현이다(Issue #191). */
@Service
class JobBookmarkAudienceQueryPortImpl(
    private val memberJobPreferenceRepository: MemberJobPreferenceRepository,
) : JobBookmarkAudienceQueryPort {
    @Transactional(readOnly = true)
    override fun findBookmarkedMemberIds(jobId: Long): List<Long> =
        memberJobPreferenceRepository.findMemberIdsByJobIdAndBookmarkedTrue(jobId)
}
