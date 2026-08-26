package team.inreok.getiserver.domain.member.service.impl

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.member.query.PortfolioTargetMemberProfile
import team.inreok.getiserver.domain.member.query.PortfolioTargetMemberProfileQueryPort
import team.inreok.getiserver.domain.member.repository.MemberRepository

/**
 * `portfolio` Module에 공개된 조회 계약([PortfolioTargetMemberProfileQueryPort])의 구현이다.
 * [MemberRepository.findAllById]로 한 번에 조회해 표시용 Snapshot으로 변환한다.
 */
@Service
class PortfolioTargetMemberProfileQueryPortImpl(
    private val memberRepository: MemberRepository,
) : PortfolioTargetMemberProfileQueryPort {
    @Transactional(readOnly = true)
    override fun findProfiles(memberIds: Set<Long>): Map<Long, PortfolioTargetMemberProfile> {
        if (memberIds.isEmpty()) return emptyMap()
        return memberRepository
            .findAllById(memberIds)
            .associate { member ->
                val id = requireNotNull(member.id) { "저장된 Member는 id를 가져야 합니다." }
                id to
                    PortfolioTargetMemberProfile(
                        memberId = id,
                        name = member.name,
                        cohort = member.cohort,
                        department = member.department?.name,
                    )
            }
    }
}
