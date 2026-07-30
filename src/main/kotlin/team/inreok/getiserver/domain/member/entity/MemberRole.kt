package team.inreok.getiserver.domain.member.entity

import jakarta.persistence.Column
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import java.time.LocalDateTime

@Entity
@Table(name = "member_roles")
class MemberRole(
    @EmbeddedId
    val id: MemberRoleId,
) {
    @CreationTimestamp
    @Column(name = "granted_at", nullable = false, updatable = false)
    var grantedAt: LocalDateTime? = null
}
