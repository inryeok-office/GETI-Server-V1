package team.inreok.getiserver.domain.application.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import team.inreok.getiserver.domain.application.entity.type.JobApplicationStatus
import java.time.LocalDateTime

/**
 * 지원서 상태가 바뀔 때마다 1건씩 남기는 불변 이력이다(Issue #133). 학생 Action
 * (`JobApplicationAction`)과 교사 Action(`JobApplicationAdminAction`)이 서로 다른 Enum이라
 * [action]은 그 이름(.name)을 그대로 저장한다 — DB 물리 제약으로 두 Enum을 동시에 강제하지
 * 않는다(V20 Migration 주석 참고). Update/삭제 없이 Insert만 발생한다.
 */
@Entity
@Table(name = "job_application_status_histories")
class JobApplicationStatusHistory(
    @Column(name = "application_id", nullable = false)
    var applicationId: Long,
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", nullable = false, length = 30)
    var fromStatus: JobApplicationStatus,
    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 30)
    var toStatus: JobApplicationStatus,
    @Column(nullable = false, length = 30)
    var action: String,
    @Column(name = "actor_member_id", nullable = false)
    var actorMemberId: Long,
    @Column(length = 1000)
    var reason: String? = null,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime? = null
}
