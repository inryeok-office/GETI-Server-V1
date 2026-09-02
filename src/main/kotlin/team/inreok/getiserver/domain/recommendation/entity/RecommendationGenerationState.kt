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
import org.hibernate.annotations.UpdateTimestamp
import team.inreok.getiserver.domain.recommendation.entity.type.RecommendationGenerationStatus
import java.time.LocalDateTime

/**
 * 회원 1명의 마지막 일일 추천 Generation 실행 상태다(Recommendation R4, Issue #160). Daily
 * Scheduler가 `RecommendationGenerationRunner`를 통해 회원 1명을 처리할 때마다 이 Row 하나를
 * GENERATING -> READY/EMPTY 또는 GENERATING -> FAILED로 갱신한다.
 *
 * `Recommendation`(추천 결과 Row 자체)과 다른 Table인 이유: 이 상태는 오늘자 추천이 하나도 없어도
 * (EMPTY) 존재해야 하고, 실패(FAILED)했을 때는 오히려 `Recommendation` Row가 없어야 하므로
 * `Recommendation` Row의 존재 여부만으로는 세 가지 상태(EMPTY/FAILED/아직 실행 전)를 구분할 수
 * 없다.
 */
@Entity
@Table(
    name = "recommendation_generation_states",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_recommendation_generation_states_member_id",
            columnNames = ["member_id"],
        ),
    ],
)
class RecommendationGenerationState(
    @Column(name = "member_id", nullable = false)
    var memberId: Long,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var status: RecommendationGenerationStatus,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    /** 마지막으로 READY/EMPTY로 성공한 시각이다. 실패해도 이전 성공 시각은 지우지 않는다(마지막
     * 성공 시점을 계속 보여줄 수 있도록). */
    @Column(name = "generated_at")
    var generatedAt: LocalDateTime? = null

    /** 마지막으로 FAILED가 된 시각이다. 이후 성공해도 지우지 않는다(과거 실패 이력 보존). */
    @Column(name = "last_failure_at")
    var lastFailureAt: LocalDateTime? = null

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime? = null
}
