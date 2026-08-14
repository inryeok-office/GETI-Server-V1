package team.inreok.getiserver.domain.application.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime

/**
 * SUBMIT/RESUBMIT 시점의 답변을 불변으로 고정한 Snapshot이다(Issue #133). `job_applications`의
 * `answers`는 그 이후 `saveDraft`로 재작성될 수 있는 "현재 값"이라 재제출 이력을 재구성할 수
 * 없어, 제출이 성공할 때마다 이 Table에 새 Row(`submissionNumber` 증가)를 추가한다. Form
 * 질문 구조는 [formId]+[formVersion](둘 다 `createDraft` 이후 바뀌지 않음)으로 `FormVersion`을
 * 다시 조회해 재현하고, 지원자 정보는 `JobApplication`의 초안 생성 시점 스냅샷 Column(V12
 * Migration)이 이미 고정값이라 여기서 중복 저장하지 않는다(V20 Migration 주석 참고).
 */
@Entity
@Table(
    name = "job_application_submissions",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_job_application_submissions_application_number",
            columnNames = ["application_id", "submission_number"],
        ),
    ],
)
class JobApplicationSubmission(
    @Column(name = "application_id", nullable = false)
    var applicationId: Long,
    @Column(name = "submission_number", nullable = false)
    var submissionNumber: Int,
    @Column(name = "form_id")
    var formId: Long?,
    @Column(name = "form_version")
    var formVersion: Int?,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    var answers: String,
    @Column(name = "submitted_at", nullable = false)
    var submittedAt: LocalDateTime,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
}
