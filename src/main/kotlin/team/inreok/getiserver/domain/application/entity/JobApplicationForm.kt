package team.inreok.getiserver.domain.application.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.LocalDateTime

/**
 * 공고-양식 연결이다(요구사항 6절). 공고 하나당 활성 양식 하나(1:1) — `jobId`가 PK라 재연결은
 * Row 갱신(UPSERT)으로 처리한다. `jobId`는 Job 도메인 소유 Table을 가리키는 평범한 FK Column이고
 * JPA 연관관계를 만들지 않는다(Modulith 경계 유지, `docs/architecture/erd.md` 원칙).
 */
@Entity
@Table(name = "job_application_forms")
class JobApplicationForm(
    @Id
    @Column(name = "job_id")
    var jobId: Long,
    @Column(name = "form_id", nullable = false)
    var formId: Long,
    @Column(name = "linked_by_member_id", nullable = false)
    var linkedByMemberId: Long,
) {
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime? = null

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime? = null
}
