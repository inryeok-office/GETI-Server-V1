package team.inreok.getiserver.domain.member.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import java.time.LocalDateTime

// member_majors/member_tech_stacks(복합키 Join Table)와 달리 순서(order)를 가진 독립 Row
// 목록이라 Surrogate Key를 쓴다. Member Entity를 직접 참조(@ManyToOne)하지 않고 member_majors와
// 동일하게 memberId만 보관해 조회 시 불필요하게 Member를 함께 로딩하지 않는다(Issue #220).
@Entity
@Table(name = "member_profile_links")
class MemberProfileLink(
    @Column(name = "member_id", nullable = false)
    val memberId: Long,
    @Column(nullable = false, length = 100)
    var label: String,
    @Column(nullable = false, length = 2000)
    var url: String,
    @Column(name = "display_order", nullable = false)
    var displayOrder: Int,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime? = null
}
