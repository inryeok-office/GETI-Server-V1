package team.inreok.getiserver.domain.recommendation.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.UpdateTimestamp
import java.time.LocalDateTime

/**
 * 회원 1명의 추천 기능 ON/OFF 설정이다(Recommendation R3, Issue #152). Row가 없으면 아직 설정한
 * 적 없는 회원이라 `RecommendationServiceImpl`이 default(false)로 해석한다 -- 가입 시 전체
 * 회원에게 Row를 만들어 두지 않고, 실제로 설정을 바꾸는 시점에만 만든다(Lazy Create).
 *
 * `member_job_preferences`(memberId+jobId 복합 PK, Job 단위)와 다른 Table인 이유: 이 설정은
 * Job과 무관하게 회원 1명당 하나뿐이라 억지로 기존 Table에 끼워 넣지 않았다.
 */
@Entity
@Table(
    name = "recommendation_preferences",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_recommendation_preferences_member_id",
            columnNames = ["member_id"],
        ),
    ],
)
class RecommendationPreference(
    @Column(name = "member_id", nullable = false)
    var memberId: Long,
    @Column(nullable = false)
    var enabled: Boolean,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime? = null
}
