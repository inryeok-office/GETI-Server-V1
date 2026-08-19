package team.inreok.getiserver.domain.recommendation.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import team.inreok.getiserver.domain.recommendation.entity.MemberJobPreference
import team.inreok.getiserver.domain.recommendation.entity.MemberJobPreferenceId

interface MemberJobPreferenceRepository : JpaRepository<MemberJobPreference, MemberJobPreferenceId> {
    // exclusion이 THIS_JOB이든 SIMILAR_JOBS든 값이 있으면 "관심 없음" 처리된 공고다(R1/R2
    // 설계 -- 두 값 모두 해당 공고를 추천 후보에서 제외하는 Hard Filter 대상). Batch로 한 번에
    // 조회해 회원의 Candidate 수만큼 반복 조회하지 않는다(N+1 방지).
    @Query(
        """
        SELECT p.id.jobId FROM MemberJobPreference p
        WHERE p.id.memberId = :memberId AND p.exclusion IS NOT NULL
        """,
    )
    fun findExcludedJobIdsByMemberId(
        @Param("memberId") memberId: Long,
    ): List<Long>
}
