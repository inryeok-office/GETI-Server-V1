package team.inreok.getiserver.domain.recommendation.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import team.inreok.getiserver.domain.recommendation.entity.RecommendationGenerationState
import java.time.LocalDateTime

interface RecommendationGenerationStateRepository : JpaRepository<RecommendationGenerationState, Long> {
    fun findByMemberId(memberId: Long): RecommendationGenerationState?

    /**
     * 회원 1명의 일일 추천 Generation을 시작하며 상태를 GENERATING으로 Upsert한다(Recommendation
     * R4, Issue #160). `RecommendationPreferenceRepository.upsert`와 같은 이유로 원자적
     * `INSERT ... ON CONFLICT`를 쓴다 -- Scheduler가 중복 실행되어 같은 회원을 거의 동시에 두 번
     * 처리하기 시작해도 경합 없이 마지막 시작 시각으로 수렴한다. `generated_at`/`last_failure_at`은
     * 건드리지 않는다(과거 성공/실패 이력을 그대로 보존).
     */
    @Modifying(clearAutomatically = true)
    @Query(
        value = """
            INSERT INTO recommendation_generation_states (member_id, status, updated_at)
            VALUES (:memberId, 'GENERATING', now())
            ON CONFLICT (member_id) DO UPDATE SET status = 'GENERATING', updated_at = now()
            """,
        nativeQuery = true,
    )
    fun markGenerating(
        @Param("memberId") memberId: Long,
    )

    /** 회원 1명의 일일 추천 Generation이 성공적으로 끝났을 때 [status](READY 또는 EMPTY)와
     * [generatedAt]을 Upsert한다. */
    @Modifying(clearAutomatically = true)
    @Query(
        value = """
            INSERT INTO recommendation_generation_states (member_id, status, generated_at, updated_at)
            VALUES (:memberId, :status, :generatedAt, now())
            ON CONFLICT (member_id) DO UPDATE
                SET status = :status, generated_at = :generatedAt, updated_at = now()
            """,
        nativeQuery = true,
    )
    fun markCompleted(
        @Param("memberId") memberId: Long,
        @Param("status") status: String,
        @Param("generatedAt") generatedAt: LocalDateTime,
    )

    /** 회원 1명의 일일 추천 Generation이 실패했을 때 상태를 FAILED로 Upsert한다. [generatedAt]은
     * 건드리지 않아 마지막 성공 시각을 계속 보여줄 수 있다. */
    @Modifying(clearAutomatically = true)
    @Query(
        value = """
            INSERT INTO recommendation_generation_states (member_id, status, last_failure_at, updated_at)
            VALUES (:memberId, 'FAILED', :failedAt, now())
            ON CONFLICT (member_id) DO UPDATE
                SET status = 'FAILED', last_failure_at = :failedAt, updated_at = now()
            """,
        nativeQuery = true,
    )
    fun markFailed(
        @Param("memberId") memberId: Long,
        @Param("failedAt") failedAt: LocalDateTime,
    )
}
