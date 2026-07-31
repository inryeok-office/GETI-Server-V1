package team.inreok.getiserver.domain.member.entity

import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "member_majors")
class MemberMajor(
    @EmbeddedId
    val id: MemberMajorId,
)
