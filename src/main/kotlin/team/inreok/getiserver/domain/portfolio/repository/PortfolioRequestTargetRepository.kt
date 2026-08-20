package team.inreok.getiserver.domain.portfolio.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import team.inreok.getiserver.domain.portfolio.entity.PortfolioRequestTarget

interface PortfolioRequestTargetRepository : JpaRepository<PortfolioRequestTarget, Long> {
    fun findAllByRequestId(requestId: Long): List<PortfolioRequestTarget>

    fun existsByRequestIdAndStudentMemberId(
        requestId: Long,
        studentMemberId: Long,
    ): Boolean

    fun countByRequestId(requestId: Long): Long

    /**
     * 요청 수정 시 대상 학생 집합을 통째로 교체하기 위해 기존 대상을 모두 지운다(DRAFT 한정, §27).
     * 파생 삭제(기존 Row를 한 건씩 조회 후 삭제)가 아니라 한 번의 DELETE로 실행되도록 벌크 Query를
     * 쓴다 -- 대상이 많아도 `1 SELECT + N DELETE`로 늘어나지 않는다.
     */
    @Modifying
    @Query("DELETE FROM PortfolioRequestTarget t WHERE t.requestId = :requestId")
    fun deleteByRequestId(
        @Param("requestId") requestId: Long,
    )

    /**
     * 이번 Page 요청들의 대상 학생 수를 한 번에 집계한다(N+1 방지, §35). 대상이 없는 requestId는
     * 결과에서 빠지므로 호출 측이 0으로 취급한다.
     */
    @Query(
        """
        SELECT t.requestId AS requestId, COUNT(t) AS count
        FROM PortfolioRequestTarget t
        WHERE t.requestId IN :requestIds
        GROUP BY t.requestId
        """,
    )
    fun countGroupedByRequestId(
        @Param("requestIds") requestIds: Collection<Long>,
    ): List<RequestCountProjection>
}
