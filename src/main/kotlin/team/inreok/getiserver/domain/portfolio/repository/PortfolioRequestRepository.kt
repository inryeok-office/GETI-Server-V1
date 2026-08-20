package team.inreok.getiserver.domain.portfolio.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import team.inreok.getiserver.domain.portfolio.entity.PortfolioRequest
import team.inreok.getiserver.domain.portfolio.entity.type.PortfolioRequestStatus

interface PortfolioRequestRepository : JpaRepository<PortfolioRequest, Long> {
    /** Soft Delete되지 않은 요청만 조회한다. 삭제된 요청은 API에서 존재하지 않는 것으로 취급한다. */
    fun findByIdAndDeletedAtIsNull(id: Long): PortfolioRequest?

    /**
     * 관리자(교사·개발자) 목록 조회다. 삭제된 요청은 제외하고, [status]가 있으면 그 상태만 좁힌다.
     * 최신 생성 순으로 정렬한다.
     */
    @Query(
        """
        SELECT r FROM PortfolioRequest r
        WHERE r.deletedAt IS NULL
          AND (:status IS NULL OR r.status = :status)
        ORDER BY r.createdAt DESC, r.id DESC
        """,
    )
    fun findAllForAdmin(
        @Param("status") status: PortfolioRequestStatus?,
        pageable: Pageable,
    ): Page<PortfolioRequest>

    /**
     * 학생 목록 조회다. 학생은 자신이 대상([PortfolioRequestTarget])인 요청만, 그리고 공개
     * 이후 상태([visibleStatuses] = PUBLISHED/CLOSED)만 볼 수 있다(요구사항 §9 "자신이 Target인
     * Request만", DRAFT 미노출). [status]가 있으면 그 상태만 추가로 좁힌다.
     */
    @Query(
        """
        SELECT r FROM PortfolioRequest r
        WHERE r.deletedAt IS NULL
          AND r.status IN :visibleStatuses
          AND (:status IS NULL OR r.status = :status)
          AND EXISTS (
            SELECT 1 FROM PortfolioRequestTarget t
            WHERE t.requestId = r.id AND t.studentMemberId = :studentMemberId
          )
        ORDER BY r.createdAt DESC, r.id DESC
        """,
    )
    fun findAllForStudent(
        @Param("studentMemberId") studentMemberId: Long,
        @Param("visibleStatuses") visibleStatuses: Collection<PortfolioRequestStatus>,
        @Param("status") status: PortfolioRequestStatus?,
        pageable: Pageable,
    ): Page<PortfolioRequest>
}
