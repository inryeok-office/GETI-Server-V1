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
import team.inreok.getiserver.domain.collector.entity.type.CollectionRunStatus
import team.inreok.getiserver.domain.collector.entity.type.CollectorAction
import java.time.LocalDateTime

// 기존 domain.collector.entity.JobCollectionRun(Foundation ERD Placeholder, PR #43)은 이미 병합된
// CoreDomainSchemaIntegrationTest가 참조하고 있어 그대로 두고 수정하지 않는다. 이 Entity는 실제
// 운영 API(GET/POST /api/v1/admin/collection-runs 등)가 요구하는 필드(sourceId 참조, action,
// partialQualityCount)를 담기 위한 별도 Table이다. 최종 보고에 두 Table의 관계를 정리해 둔다.
@Entity
@Table(name = "collection_runs")
class CollectionRun(
    @Column(name = "source_id", nullable = false)
    var sourceId: Long,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var action: CollectorAction,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var status: CollectionRunStatus = CollectionRunStatus.PENDING,
    @Column(name = "started_at", nullable = false)
    var startedAt: LocalDateTime,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(name = "total_count", nullable = false)
    var totalCount: Int = 0

    @Column(name = "success_count", nullable = false)
    var successCount: Int = 0

    @Column(name = "failure_count", nullable = false)
    var failureCount: Int = 0

    // V30 이전 실행은 생성/갱신 결과를 분리해 저장하지 않았으므로 null로 역사적 불확실성을 보존한다.
    // 신규 실행은 0에서 시작해 완료 시 실제 Upsert 결과로 갱신한다.
    @Column(name = "created_count")
    var createdCount: Int? = 0

    @Column(name = "updated_count")
    var updatedCount: Int? = 0

    @Column(name = "partial_quality_count", nullable = false)
    var partialQualityCount: Int = 0

    @Column(name = "finished_at")
    var finishedAt: LocalDateTime? = null

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime? = null

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime? = null
}
