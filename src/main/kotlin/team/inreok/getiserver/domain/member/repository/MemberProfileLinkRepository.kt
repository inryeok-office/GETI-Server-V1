package team.inreok.getiserver.domain.member.repository

import org.springframework.data.jpa.repository.JpaRepository
import team.inreok.getiserver.domain.member.entity.MemberProfileLink

interface MemberProfileLinkRepository : JpaRepository<MemberProfileLink, Long> {
    fun findAllByMemberIdOrderByDisplayOrderAsc(memberId: Long): List<MemberProfileLink>

    fun deleteAllByMemberId(memberId: Long)
}
