package team.inreok.getiserver.domain.member.repository

import org.springframework.data.jpa.repository.JpaRepository
import team.inreok.getiserver.domain.member.entity.MemberMajor
import team.inreok.getiserver.domain.member.entity.MemberMajorId

interface MemberMajorRepository : JpaRepository<MemberMajor, MemberMajorId> {
    fun findAllByIdMemberId(memberId: Long): List<MemberMajor>

    fun deleteAllByIdMemberId(memberId: Long)
}
