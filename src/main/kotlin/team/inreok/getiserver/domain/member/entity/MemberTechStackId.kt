package team.inreok.getiserver.domain.member.entity

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import java.io.Serializable

@Embeddable
data class MemberTechStackId(
    @Column(name = "member_id")
    val memberId: Long,
    @Column(name = "tech_stack_id")
    val techStackId: Long,
) : Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}
