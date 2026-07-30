package team.inreok.getiserver.domain.member.entity

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import team.inreok.getiserver.domain.member.entity.type.RoleType
import java.io.Serializable

@Embeddable
data class MemberRoleId(
    @Column(name = "member_id")
    val memberId: Long,
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    val role: RoleType,
) : Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}
