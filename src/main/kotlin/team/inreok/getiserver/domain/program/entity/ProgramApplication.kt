package team.inreok.getiserver.domain.program.entity

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
import team.inreok.getiserver.domain.program.entity.type.ProgramApplicationStatus
import java.time.LocalDateTime

@Entity
@Table(name = "program_applications")
class ProgramApplication(
    @Column(name = "program_id", nullable = false)
    var programId: Long,
    @Column(name = "applicant_member_id", nullable = false)
    var applicantMemberId: Long,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var status: ProgramApplicationStatus = ProgramApplicationStatus.APPLIED,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    var answers: String? = null

    // 신청 당시 연결된 Form의 id/버전 Snapshot이다(요구사항 11절/20절). 이후 Form이 바뀌거나
    // 새 버전이 생겨도 이미 접수된 신청에는 영향을 주지 않는다.
    @Column(name = "form_id")
    var formId: Long? = null

    @Column(name = "form_version")
    var formVersion: Int? = null

    @CreationTimestamp
    @Column(name = "applied_at", nullable = false, updatable = false)
    var appliedAt: LocalDateTime? = null

    @Column(name = "canceled_at")
    var canceledAt: LocalDateTime? = null

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime? = null
}
