package team.inreok.getiserver.domain.recommendation.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import team.inreok.getiserver.domain.recommendation.entity.RecommendationPreference

interface RecommendationPreferenceRepository : JpaRepository<RecommendationPreference, Long> {
    fun findByMemberId(memberId: Long): RecommendationPreference?

    /** Daily Scheduler(R4, Issue #160)가 추천 활성화된 회원부터 좁혀 오기 위한 조회다. 이 결과를
     * `RecommendationAudienceQueryPort.filterEligibleStudentIds`에 넘겨 실제 대상 재학생만 다시
     * 추린다. */
    @Query("SELECT rp.memberId FROM RecommendationPreference rp WHERE rp.enabled = true")
    fun findMemberIdsByEnabledTrue(): List<Long>

    /**
     * ON/OFF 설정을 원자적으로 Upsert한다(코드리뷰 반영, PR #153).
     *
     * 처음에는 `findByMemberId` -> 없으면 새로 생성 -> `save` 순서로 구현했는데, 아직 설정한 적
     * 없는 회원이 `PATCH /settings`를 거의 동시에 두 번 보내면(더블 클릭·재시도) 두 요청 모두
     * `findByMemberId`에서 null을 받아 각각 새 Row를 `save`하려 한다. 뒤 요청은
     * `uk_recommendation_preferences_member_id` 위반으로 `DataIntegrityViolationException`이
     * 되어 API의 멱등 성공 계약이 깨진다. PostgreSQL 하나의 원자적 Statement인
     * `INSERT ... ON CONFLICT`로 처리해 이 경합 자체를 없앤다 -- try-catch 후 같은 Transaction
     * 안에서 재조회하는 방식은 PostgreSQL이 제약 위반 이후 Transaction을 Abort 상태로 만들어
     * (Savepoint 없이는) 재조회 자체가 실패하므로 채택하지 않았다.
     *
     * `clearAutomatically = true`가 필요하다 -- 이 Query는 Native라 Hibernate가 변경을 추적하지
     * 못해, 같은 Transaction 안에서 이미 Entity가 Persistence Context에 올라와 있으면(예:
     * `saveAndFlush`로 만든 직후) 그 뒤의 `findByMemberId`가 DB의 새 값 대신 Context에 남은
     * 이전 Entity를 그대로 돌려준다(Integration Test로 재현·확인).
     */
    @Modifying(clearAutomatically = true)
    @Query(
        value = """
            INSERT INTO recommendation_preferences (member_id, enabled, updated_at)
            VALUES (:memberId, :enabled, now())
            ON CONFLICT (member_id) DO UPDATE SET enabled = :enabled, updated_at = now()
            """,
        nativeQuery = true,
    )
    fun upsert(
        @Param("memberId") memberId: Long,
        @Param("enabled") enabled: Boolean,
    )
}
