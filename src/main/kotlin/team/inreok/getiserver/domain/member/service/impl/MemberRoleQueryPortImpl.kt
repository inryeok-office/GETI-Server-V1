package team.inreok.getiserver.domain.member.service.impl

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.member.entity.type.RoleType
import team.inreok.getiserver.domain.member.query.MemberRoleQueryPort
import team.inreok.getiserver.domain.member.repository.MemberRoleRepository

/** `program` 등 다른 Domain Module에 공개된 조회 계약([MemberRoleQueryPort])의 구현이다. */
@Service
class MemberRoleQueryPortImpl(
    private val memberRoleRepository: MemberRoleRepository,
) : MemberRoleQueryPort {
    @Transactional(readOnly = true)
    override fun findRoles(memberId: Long): Set<RoleType> =
        memberRoleRepository.findAllByIdMemberId(memberId).map { it.id.role }.toSet()
}
