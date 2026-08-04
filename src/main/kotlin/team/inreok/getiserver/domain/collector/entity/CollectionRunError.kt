package team.inreok.getiserver.domain.collector.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

// message에는 우리 코드가 직접 작성한 안전한 설명만 담는다. 외부 원문 응답 전체, 인증 Header,
// API Key, 요청 URL은 저장하지 않는다(docs/ai/security-policy.md). missingFields는 조회가
// 필요 없는 단순 목록이라 JSONB 대신 콤마 구분 문자열로 최소하게 저장한다.
@Entity
@Table(name = "collection_run_errors")
class CollectionRunError(
    @Column(name = "run_id", nullable = false)
    var runId: Long,
    @Column(nullable = false, length = 100)
    var code: String,
    @Column(nullable = false, length = 1000)
    var message: String,
    @Column(name = "occurred_at", nullable = false)
    var occurredAt: LocalDateTime,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(name = "external_job_id", length = 255)
    var externalJobId: String? = null

    @Column(name = "missing_fields", length = 1000)
    var missingFields: String? = null
}
