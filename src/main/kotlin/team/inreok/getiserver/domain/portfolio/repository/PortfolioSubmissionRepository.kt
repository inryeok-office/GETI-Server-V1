package team.inreok.getiserver.domain.portfolio.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import team.inreok.getiserver.domain.portfolio.entity.PortfolioSubmission
import team.inreok.getiserver.domain.portfolio.entity.type.PortfolioSubmissionStatus

interface PortfolioSubmissionRepository : JpaRepository<PortfolioSubmission, Long> {
    fun findByRequestIdAndMemberId(
        requestId: Long,
        memberId: Long,
    ): PortfolioSubmission?

    /** 실제 제출 완료(SUBMITTED) 상태만 센다 — DRAFT(임시저장)는 제출 수에 포함하지 않는다(요구사항 §20). */
    fun countByRequestIdAndStatus(
        requestId: Long,
        status: PortfolioSubmissionStatus,
    ): Long

    /**
     * 이번 Page 요청들의 제출 완료(SUBMITTED) 수를 한 번에 집계한다(N+1 방지, §35). 제출이 없는
     * requestId는 결과에서 빠지므로 호출 측이 0으로 취급한다.
     */
    @Query(
        """
        SELECT s.requestId AS requestId, COUNT(s) AS count
        FROM PortfolioSubmission s
        WHERE s.requestId IN :requestIds AND s.status = :status
        GROUP BY s.requestId
        """,
    )
    fun countGroupedByRequestIdAndStatus(
        @Param("requestIds") requestIds: Collection<Long>,
        @Param("status") status: PortfolioSubmissionStatus,
    ): List<RequestCountProjection>
}
