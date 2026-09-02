package team.inreok.getiserver.domain.member.service.impl

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.member.dto.AdminMemberListResponse
import team.inreok.getiserver.domain.member.dto.AdminMemberSummaryResponse
import team.inreok.getiserver.domain.member.entity.type.MemberStatus
import team.inreok.getiserver.domain.member.entity.type.RoleType
import team.inreok.getiserver.domain.member.repository.MemberRepository
import team.inreok.getiserver.domain.member.service.AdminMemberQueryService

@Service
class AdminMemberQueryServiceImpl(
    private val memberRepository: MemberRepository,
) : AdminMemberQueryService {
    @Transactional(readOnly = true)
    override fun listActiveMembersByRole(role: RoleType): AdminMemberListResponse {
        val members =
            memberRepository
                .findAllByStatusAndRoleOrderByName(MemberStatus.ACTIVE, role)
                .map { AdminMemberSummaryResponse(memberId = requireNotNull(it.id), name = it.name) }
        return AdminMemberListResponse(members = members)
    }
}
