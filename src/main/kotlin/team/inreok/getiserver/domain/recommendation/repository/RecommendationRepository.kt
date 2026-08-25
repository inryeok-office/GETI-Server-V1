package team.inreok.getiserver.domain.recommendation.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import team.inreok.getiserver.domain.recommendation.entity.Recommendation
import team.inreok.getiserver.domain.recommendation.entity.type.SuitabilityLevel
import java.time.LocalDate
import java.time.LocalDateTime

interface RecommendationRepository : JpaRepository<Recommendation, Long> {
    fun findByMemberIdAndJobIdAndRecommendationDate(
        memberId: Long,
        jobId: Long,
        recommendationDate: LocalDate,
    ): Recommendation?

    /** 내 추천 조회(Notion `GET /api/v1/me/job-recommendations`)의 Pagination + `suitabilityLevel`
     * Filter다(Issue #155). 전체 결과를 메모리로 가져와 자르지 않고 Query 단계에서 Page를
     * 만든다. [suitabilityLevel]이 null이면 전체를 대상으로 한다(`MemberRepository`의 nullable
     * Filter 관례와 동일). */
    @Query(
        """
        SELECT r FROM Recommendation r
        WHERE r.memberId = :memberId AND r.recommendationDate = :date
          AND (:suitabilityLevel IS NULL OR r.suitability = :suitabilityLevel)
        ORDER BY r.rank ASC
        """,
    )
    fun findAllByMemberIdAndRecommendationDate(
        @Param("memberId") memberId: Long,
        @Param("date") date: LocalDate,
        @Param("suitabilityLevel") suitabilityLevel: SuitabilityLevel?,
        pageable: Pageable,
    ): Page<Recommendation>

    /** 조회 응답의 `generatedAt`은 Notion 계약상 현재 Page/Filter와 무관하게 "오늘자 추천
     * 결과가 생성된 시각" 하나다 -- Filter로 결과가 줄어들거나 다른 Page를 보고 있어도 값이
     * 바뀌면 안 되므로, 목록 조회([findAllByMemberIdAndRecommendationDate])와 별개로 오늘자
     * 전체에서 MAX만 뽑는다(불필요하게 전체 Row를 읽지 않는다). */
    @Query(
        """
        SELECT MAX(r.createdAt) FROM Recommendation r
        WHERE r.memberId = :memberId AND r.recommendationDate = :date
        """,
    )
    fun findMaxCreatedAtByMemberIdAndRecommendationDate(
        @Param("memberId") memberId: Long,
        @Param("date") date: LocalDate,
    ): LocalDateTime?

    // 회원 1명의 추천 결과를 하루치 Snapshot으로 통째로 교체하기 위한 삭제다(R2 Persistence
    // 설계, `RecommendationGenerationServiceImpl`이 삭제 -> 재삽입을 한 Transaction에서
    // 수행한다). `MemberTechStackRepository.deleteAllByIdMemberId`와 같은 관례로 `@Modifying`
    // 없이 Spring Data의 derived delete를 그대로 쓴다.
    fun deleteAllByMemberIdAndRecommendationDate(
        memberId: Long,
        recommendationDate: LocalDate,
    )

    // 관심 없음(THIS_JOB/SIMILAR_JOBS) 설정 시 현재 노출 중인 Recommendation을 즉시 제거하기
    // 위한 삭제다(R3, Issue #152). 조회 API가 항상 오늘자 결과만 보여주므로(recommendationDate
    // 제한 없이) 그 회원의 해당 Job Recommendation을 통째로 지워도 무방하다 -- 과거 날짜 Row가
    // 남아 있어도 조회에 다시 나타나지 않는다. Hard Filter(`computeExclusionReason`)가 다음
    // 생성부터 이 Job을 이미 걸러내므로 재생성 때 다시 만들어지지도 않는다.
    fun deleteAllByMemberIdAndJobId(
        memberId: Long,
        jobId: Long,
    )

    /** SIMILAR_JOBS 등록 즉시 반영(R6, Issue #167)에서 "현재 오늘자 Recommendation 중 어떤
     * Job이 유사 판정 대상인지"를 계산하기 위한 후보 jobId 목록이다. Job 개수만큼 반복 조회하지
     * 않도록 회원 1명당 한 번만 호출한다. */
    @Query(
        """
        SELECT r.jobId FROM Recommendation r
        WHERE r.memberId = :memberId AND r.recommendationDate = :date
        """,
    )
    fun findJobIdsByMemberIdAndRecommendationDate(
        @Param("memberId") memberId: Long,
        @Param("date") date: LocalDate,
    ): List<Long>

    /** SIMILAR_JOBS 등록 즉시 반영에서 유사로 판정된 여러 Job의 오늘자 Recommendation을 한 번에
     * 지운다(R6, Issue #167) -- [jobIds] 개수만큼 [deleteAllByMemberIdAndJobId]를 반복 호출하지
     * 않는다(N+1 방지). */
    fun deleteAllByMemberIdAndJobIdIn(
        memberId: Long,
        jobIds: Set<Long>,
    )
}
