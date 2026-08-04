package team.inreok.getiserver.domain.search.entity

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
import team.inreok.getiserver.domain.search.entity.type.SearchIndexOperation
import java.time.LocalDateTime

/**
 * 공고 색인(UPSERT)/색인 제거(DELETE)가 Elasticsearch 오류로 실패했을 때만 만들어지는 재시도
 * 대상이다. 성공하면 이 Row를 지운다 — 이력이 아니라 "현재 미해결 실패" 목록이다(Issue #69).
 */
@Entity
@Table(name = "search_index_failures")
class SearchIndexFailure(
    @Column(name = "job_id", nullable = false, unique = true)
    var jobId: Long,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var operation: SearchIndexOperation,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(name = "attempt_count", nullable = false)
    var attemptCount: Int = 0

    /** null이면 최대 재시도 횟수를 넘겨 더 이상 재시도하지 않는 영구 실패다. */
    @Column(name = "next_retry_at")
    var nextRetryAt: LocalDateTime? = null

    @Column(name = "last_error", length = 1000)
    var lastError: String? = null

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime? = null

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime? = null
}
