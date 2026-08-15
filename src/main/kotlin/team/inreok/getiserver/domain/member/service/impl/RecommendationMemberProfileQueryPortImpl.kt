package team.inreok.getiserver.domain.member.service.impl

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.member.query.RecommendationMemberProfileQueryPort
import team.inreok.getiserver.domain.member.query.RecommendationMemberProfileSnapshot
import team.inreok.getiserver.domain.member.repository.MemberRepository
import team.inreok.getiserver.domain.member.repository.MemberTechStackRepository

/** [RecommendationMemberProfileQueryPort]의 구현이다(Recommendation R2, Issue #148). */
@Service
class RecommendationMemberProfileQueryPortImpl(
    private val memberRepository: MemberRepository,
    private val memberTechStackRepository: MemberTechStackRepository,
) : RecommendationMemberProfileQueryPort {
    @Transactional(readOnly = true)
    override fun findById(memberId: Long): RecommendationMemberProfileSnapshot? {
        val member = memberRepository.findById(memberId).orElse(null) ?: return null
        val techStackIds =
            memberTechStackRepository
                .findAllByIdMemberId(memberId)
                .map { it.id.techStackId }
                .toSet()
        return RecommendationMemberProfileSnapshot(
            memberId = requireNotNull(member.id) { "저장된 Member는 id를 가져야 합니다." },
            status = member.status.name,
            grade = member.grade,
            techStackIds = techStackIds,
        )
    }
}
