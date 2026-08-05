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

    // 이 아래는 Application Phase 2(Epic #75, Issue #78)에서 추가한 Column이다(V12 Migration).
    // 제출 당시 Form Snapshot 목적은 Phase 3(제출)에서 본격적으로 쓰이고, 이번 Phase는 초안
    // 생성 시점에 연결된 Form의 id/version을 기록하는 데만 사용한다.
    @Column(name = "form_id")
    var formId: Long? = null

    @Column(name = "form_version")
    var formVersion: Int? = null

    @Column(name = "privacy_consent", nullable = false)
    var privacyConsent: Boolean = false

    // 지원자 스냅샷(요구사항 8절·21절). 제출 시점이 아니라 이후 회원 프로필 수정에 영향받지
    // 않도록 초안 생성 시점 값을 그대로 보존한다. contactEmail/contactPhone은 기존 Column을
    // 연락처 스냅샷으로 그대로 쓴다.
    @Column(name = "applicant_name", length = 100)
    var applicantName: String? = null

    @Column(name = "applicant_cohort")
    var applicantCohort: Int? = null

    @Column(name = "applicant_department", length = 30)
    var applicantDepartment: String? = null

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "applicant_majors", columnDefinition = "jsonb")
    var applicantMajors: String? = null

    @Column(name = "applicant_desired_job", length = 255)
    var applicantDesiredJob: String? = null

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "applicant_tech_stacks", columnDefinition = "jsonb")
    var applicantTechStacks: String? = null

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime? = null

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime? = null
}
