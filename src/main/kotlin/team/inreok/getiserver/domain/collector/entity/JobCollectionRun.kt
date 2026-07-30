package team.inreok.getiserver.domain.collector.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import team.inreok.getiserver.domain.operation.entity.type.OperationStatus
import java.time.LocalDateTime

// collector Domain은 실행 상태 표현을 위해 operation Domain의 공개 Type(OperationStatus)을
// 명시적으로 재사용한다(다른 Domain의 entity/repository 구현을 참조하는 것이 아니라 상태 값
// Enum만 공유하는 문서화된 예외). docs/architecture/modularity.md 참고.
@Entity
@Table(name = "job_collection_runs")
class JobCollectionRun(
    @Column(name = "source_name", nullable = false, length = 255)
    var sourceName: String,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var status: OperationStatus = OperationStatus.PENDING,
    @Column(name = "started_at", nullable = false)
    var startedAt: LocalDateTime,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(name = "new_count", nullable = false)
    var newCount: Int = 0

    @Column(name = "updated_count", nullable = false)
    var updatedCount: Int = 0

    @Column(name = "failed_count", nullable = false)
    var failedCount: Int = 0

    @Column(name = "error_summary", columnDefinition = "text")
    var errorSummary: String? = null

    @Column(name = "finished_at")
    var finishedAt: LocalDateTime? = null
}
