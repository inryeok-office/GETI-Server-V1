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
import team.inreok.getiserver.domain.recommendation.entity.type.ExclusionType
import java.time.LocalDateTime

/**
 * 회원 1명의 공고 1건에 대한 북마크/관심 없음 상태다. Notion 관심 없음 API 계약(Issue #155,
 * `GET/POST/DELETE /api/v1/me/recommendation-exclusions`)이 안정적인 Long `exclusionId`를
 * 요구해 Surrogate PK([id])로 식별한다 -- (member_id, job_id) 조합의 유일성은
 * `uk_member_job_preferences_member_job`으로 그대로 보존한다(V24 Migration).
 */
@Entity
@Table(
    name = "member_job_preferences",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_member_job_preferences_member_job",
            columnNames = ["member_id", "job_id"],
        ),
    ],
)
class MemberJobPreference(
    @Column(name = "member_id", nullable = false)
    var memberId: Long,
    @Column(name = "job_id", nullable = false)
    var jobId: Long,
    @Column(nullable = false)
    var bookmarked: Boolean = false,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    var exclusion: ExclusionType? = null

    // 관심 없음이 설정된 시각이다(Notion 관심 없음 등록 Resource의 createdAt). exclusion을
    // 지울 때(RecommendationServiceImpl.removeExclusion) 함께 null로 되돌린다 --
    // updated_at은 bookmarked 등 이 Row의 다른 변경으로도 갱신되어 exclusion 자체의 설정
    // 시각과 다를 수 있다.
    @Column(name = "exclusion_created_at")
    var exclusionCreatedAt: LocalDateTime? = null

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime? = null
}
