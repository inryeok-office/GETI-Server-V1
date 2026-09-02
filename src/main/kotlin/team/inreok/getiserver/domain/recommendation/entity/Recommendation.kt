package team.inreok.getiserver.domain.recommendation.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import team.inreok.getiserver.domain.recommendation.entity.type.SuitabilityLevel
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@Table(
    name = "recommendations",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_recommendations_member_job_date",
            columnNames = ["member_id", "job_id", "recommendation_date"],
        ),
    ],
)
class Recommendation(
    @Column(name = "member_id", nullable = false)
    var memberId: Long,
    @Column(name = "job_id", nullable = false)
    var jobId: Long,
    @Column(name = "recommendation_date", nullable = false)
    var recommendationDate: LocalDate,
    @Column(nullable = false, precision = 7, scale = 4)
    var score: BigDecimal,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var suitability: SuitabilityLevel,
    // 같은 memberId+recommendationDate Batch 안에서의 순위(1부터 시작, score DESC ->
    // publishedAt DESC -> jobId DESC로 이미 확정된 순서를 그대로 저장한다). 조회 시점에 다시
    // 정렬하지 않고 그대로 ORDER BY rank로 읽을 수 있게 하기 위한 Snapshot 값이다(R2 설계).
    @Column(nullable = false)
    var rank: Int,
    // 추천 계산식이 바뀌어도 과거 추천 결과를 구분할 수 있도록 계산 당시의
    // RECOMMENDATION_ALGORITHM_VERSION을 그대로 저장한다(R2 설계).
    @Column(name = "algorithm_version", nullable = false)
    var algorithmVersion: Int,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    var reasons: String? = null

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime? = null
}
