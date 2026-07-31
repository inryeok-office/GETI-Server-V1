package team.inreok.getiserver.domain.member.entity

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import java.io.Serializable

@Embeddable
data class MemberMajorId(
    @Column(name = "member_id")
    val memberId: Long,
    @Column(name = "major_id")
    val majorId: Long,
) : Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}
