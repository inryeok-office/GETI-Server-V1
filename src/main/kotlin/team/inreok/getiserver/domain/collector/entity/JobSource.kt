package team.inreok.getiserver.domain.collector.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import team.inreok.getiserver.domain.collector.entity.type.JobSourceApprovalStatus
import team.inreok.getiserver.domain.collector.entity.type.JobSourceCode
import team.inreok.getiserver.domain.collector.entity.type.JobSourceType
import java.time.LocalDateTime

// 수집원의 설정과 상태만 담는다. 인증키 원문은 여기에 저장하지 않는다(환경변수로만 참조,
// docs/development/configuration.md). `configured`는 이 Entity의 Column이 아니라 실행 시점에
// Provider 구현 존재 여부와 환경변수 주입 상태로 계산한다(JobSourceService 참고).
@Entity
@Table(name = "job_sources")
class JobSource(
    @Enumerated(EnumType.STRING)
    @Column(name = "source_code", nullable = false, length = 30, unique = true)
    var sourceCode: JobSourceCode,
    @Column(nullable = false, length = 255)
    var name: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 30)
    var sourceType: JobSourceType,
    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", nullable = false, length = 30)
    var approvalStatus: JobSourceApprovalStatus,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(nullable = false)
    var enabled: Boolean = false

    @Column(name = "daily_request_limit")
    var dailyRequestLimit: Int? = null

    @Column(name = "last_collected_at")
    var lastCollectedAt: LocalDateTime? = null

    @Column(name = "last_success_at")
    var lastSuccessAt: LocalDateTime? = null

    @Column(name = "last_failure_at")
    var lastFailureAt: LocalDateTime? = null

    @Column(name = "last_error", length = 1000)
    var lastError: String? = null

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime? = null

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime? = null
}
