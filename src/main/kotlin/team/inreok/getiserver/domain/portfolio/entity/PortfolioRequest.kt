package team.inreok.getiserver.domain.portfolio.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.UpdateTimestamp
import org.hibernate.type.SqlTypes
import team.inreok.getiserver.domain.portfolio.entity.type.PortfolioRequestStatus
import java.time.LocalDateTime

@Entity
@Table(name = "portfolio_requests")
class PortfolioRequest(
    @Column(name = "created_by_member_id", nullable = false)
    var createdByMemberId: Long,
    @Column(nullable = false, length = 500)
    var title: String,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var status: PortfolioRequestStatus = PortfolioRequestStatus.DRAFT,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "target_condition", nullable = false, columnDefinition = "jsonb")
    var targetCondition: String,
    @Column(name = "submission_started_at", nullable = false)
    var submissionStartedAt: LocalDateTime,
    @Column(name = "submission_ended_at", nullable = false)
    var submissionEndedAt: LocalDateTime,
    @Column(name = "allow_file", nullable = false)
    var allowFile: Boolean = true,
    @Column(name = "allow_url", nullable = false)
    var allowUrl: Boolean = true,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(columnDefinition = "text")
    var description: String? = null

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime? = null

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime? = null

    @Column(name = "deleted_at")
    var deletedAt: LocalDateTime? = null
}
