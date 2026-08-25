package team.inreok.getiserver.domain.member.service.impl

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.member.entity.type.AcademicStatus
import team.inreok.getiserver.domain.member.entity.type.MemberStatus
import team.inreok.getiserver.domain.member.entity.type.RoleType
import team.inreok.getiserver.domain.member.query.RecommendationAudienceQueryPort
import team.inreok.getiserver.domain.member.repository.MemberRepository

/** [RecommendationAudienceQueryPort]의 구현이다(Recommendation R4, Issue #160). */
@Service
class RecommendationAudienceQueryPortImpl(
    private val memberRepository: MemberRepository,
) : RecommendationAudienceQueryPort {
    @Transactional(readOnly = true)
    override fun filterEligibleStudentIds(memberIds: Collection<Long>): List<Long> {
        if (memberIds.isEmpty()) return emptyList()
        return memberRepository.findIdsByIdInAndStatusAndAcademicStatusAndRole(
            memberIds = memberIds,
            status = MemberStatus.ACTIVE,
            academicStatus = AcademicStatus.ENROLLED,
            role = RoleType.STUDENT,
        )
    }
}
