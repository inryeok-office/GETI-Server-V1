package team.inreok.getiserver.domain.member.repository

import org.springframework.data.jpa.repository.JpaRepository
import team.inreok.getiserver.domain.member.entity.MemberRole
import team.inreok.getiserver.domain.member.entity.MemberRoleId

interface MemberRoleRepository : JpaRepository<MemberRole, MemberRoleId> {
    fun findAllByIdMemberId(memberId: Long): List<MemberRole>
}
