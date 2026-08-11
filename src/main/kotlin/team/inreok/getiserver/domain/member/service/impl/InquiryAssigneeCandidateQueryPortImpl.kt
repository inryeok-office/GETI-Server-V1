package team.inreok.getiserver.domain.member.service.impl

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.member.query.InquiryAssigneeCandidate
import team.inreok.getiserver.domain.member.query.InquiryAssigneeCandidateQueryPort
import team.inreok.getiserver.domain.member.repository.MemberRepository
import team.inreok.getiserver.domain.member.repository.MemberRoleRepository

/** `inquiry` Module에 공개된 조회 계약([InquiryAssigneeCandidateQueryPort])의 구현이다. */
@Service
class InquiryAssigneeCandidateQueryPortImpl(
    private val memberRepository: MemberRepository,
    private val memberRoleRepository: MemberRoleRepository,
) : InquiryAssigneeCandidateQueryPort {
    @Transactional(readOnly = true)
    override fun findById(memberId: Long): InquiryAssigneeCandidate? {
        val member = memberRepository.findById(memberId).orElse(null) ?: return null
        val roles = memberRoleRepository.findAllByIdMemberId(memberId).map { it.id.role.name }.toSet()
        return InquiryAssigneeCandidate(
            memberId = requireNotNull(member.id) { "저장된 Member는 id를 가져야 합니다." },
            name = member.name,
            roles = roles,
            status = member.status.name,
        )
    }
}
