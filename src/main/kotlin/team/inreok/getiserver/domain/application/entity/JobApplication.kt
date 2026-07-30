package team.inreok.getiserver.domain.application.entity

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
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.UpdateTimestamp
import org.hibernate.type.SqlTypes
import team.inreok.getiserver.domain.application.entity.type.JobApplicationStatus
import java.time.LocalDateTime

@Entity
@Table(
    name = "job_applications",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_job_applications_job_applicant_attempt",
            columnNames = ["job_id", "applicant_member_id", "attempt_number"],
        ),
    ],
)
class JobApplication(
    @Column(name = "job_id", nullable = false)
    var jobId: Long,
    @Column(name = "applicant_member_id", nullable = false)
    var applicantMemberId: Long,
    @Column(name = "attempt_number", nullable = false)
    var attemptNumber: Int,
    @Column(name = "contact_email", nullable = false, length = 255)
    var contactEmail: String,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    var answers: String,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var status: JobApplicationStatus = JobApplicationStatus.DRAFT,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(name = "contact_phone", length = 30)
    var contactPhone: String? = null

    @Column(name = "status_reason", columnDefinition = "text")
    var statusReason: String? = null

    @Column(name = "submitted_at")
    var submittedAt: LocalDateTime? = null

    @Column(name = "forwarded_at")
    var forwardedAt: LocalDateTime? = null

    @Column(name = "withdrawn_at")
    var withdrawnAt: LocalDateTime? = null

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime? = null

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime? = null
}
