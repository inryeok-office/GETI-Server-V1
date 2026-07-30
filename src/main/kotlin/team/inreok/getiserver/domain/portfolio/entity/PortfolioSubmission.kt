package team.inreok.getiserver.domain.portfolio.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import team.inreok.getiserver.domain.portfolio.entity.type.PortfolioSubmissionStatus
import java.time.LocalDateTime

@Entity
@Table(
    name = "portfolio_submissions",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_portfolio_submissions_request_member", columnNames = ["request_id", "member_id"]),
    ],
)
class PortfolioSubmission(
    @Column(name = "request_id", nullable = false)
    var requestId: Long,
    @Column(name = "member_id", nullable = false)
    var memberId: Long,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var status: PortfolioSubmissionStatus = PortfolioSubmissionStatus.DRAFT,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(name = "portfolio_url", length = 2000)
    var portfolioUrl: String? = null

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime? = null

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime? = null

    @Column(name = "submitted_at")
    var submittedAt: LocalDateTime? = null
}
