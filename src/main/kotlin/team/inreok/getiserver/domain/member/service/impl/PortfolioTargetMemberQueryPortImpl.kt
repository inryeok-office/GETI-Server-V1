package team.inreok.getiserver.domain.member.service.impl

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.member.entity.type.MemberStatus
import team.inreok.getiserver.domain.member.entity.type.RoleType
import team.inreok.getiserver.domain.member.query.PortfolioTargetMemberQueryPort
import team.inreok.getiserver.domain.member.repository.MemberRepository

/**
 * `portfolio` Module에 공개된 조회 계약([PortfolioTargetMemberQueryPort])의 구현이다.
 * [MemberRepository.findIdsByIdInAndStatusAndRole]을 ACTIVE/STUDENT로 고정해 감싼다.
 */
@Service
class PortfolioTargetMemberQueryPortImpl(
    private val memberRepository: MemberRepository,
) : PortfolioTargetMemberQueryPort {
    @Transactional(readOnly = true)
    override fun findStudentMemberIds(memberIds: Set<Long>): Set<Long> {
        if (memberIds.isEmpty()) return emptySet()
        return memberRepository.findIdsByIdInAndStatusAndRole(memberIds, MemberStatus.ACTIVE, RoleType.STUDENT).toSet()
    }
}
