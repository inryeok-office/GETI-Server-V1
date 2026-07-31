package team.inreok.getiserver.domain.member.repository

import org.springframework.data.jpa.repository.JpaRepository
import team.inreok.getiserver.domain.member.entity.MemberTechStack
import team.inreok.getiserver.domain.member.entity.MemberTechStackId

interface MemberTechStackRepository : JpaRepository<MemberTechStack, MemberTechStackId> {
    fun findAllByIdMemberId(memberId: Long): List<MemberTechStack>

    fun deleteAllByIdMemberId(memberId: Long)
}
