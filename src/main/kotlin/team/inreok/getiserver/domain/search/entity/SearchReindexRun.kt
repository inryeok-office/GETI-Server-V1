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
import team.inreok.getiserver.domain.search.entity.type.SearchReindexStatus
import java.time.LocalDateTime

/** 개발자가 `POST /api/v1/admin/search-actions`로 트리거하는 전체 재색인 1회 실행 이력이다(Issue #69). */
@Entity
@Table(name = "search_reindex_runs")
class SearchReindexRun {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: SearchReindexStatus = SearchReindexStatus.PENDING

    @Column(name = "total_count")
    var totalCount: Long? = null

    @Column(name = "success_count")
    var successCount: Long? = null

    @Column(name = "failure_count")
    var failureCount: Long? = null

    @Column(name = "error_message", length = 1000)
    var errorMessage: String? = null

    @Column(name = "started_at")
    var startedAt: LocalDateTime? = null

    @Column(name = "completed_at")
    var completedAt: LocalDateTime? = null

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime? = null
}
