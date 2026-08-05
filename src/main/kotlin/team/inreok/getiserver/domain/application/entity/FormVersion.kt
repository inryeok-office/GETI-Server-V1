package team.inreok.getiserver.domain.application.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime

/**
 * Form 필드 구조의 버전별 불변 스냅샷이다(요구사항 5.5절). 한 번 생성된 Row는 Update·삭제하지
 * 않는다 — 제출된 지원서는 제출 당시 버전을 그대로 참조해야 하기 때문이다. `schemaData`는 필드
 * 배열을 JSON 원문으로 저장한다(`jobs.target_condition`, `job_applications.answers`와 동일한
 * 관례). 구조화된 검증·역직렬화는 Service 계층(`FormFieldSchema`)이 담당한다.
 */
@Entity
@Table(
    name = "form_versions",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_form_versions_form_version", columnNames = ["form_id", "version"]),
    ],
)
class FormVersion(
    @Column(name = "form_id", nullable = false)
    var formId: Long,
    @Column(nullable = false)
    var version: Int,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "schema_data", nullable = false, columnDefinition = "jsonb")
    var schemaData: String,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime? = null
}
