package team.inreok.getiserver.domain.portfolio.repository

import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import team.inreok.getiserver.domain.portfolio.entity.PortfolioRequest
import team.inreok.getiserver.domain.portfolio.entity.type.PortfolioRequestStatus

interface PortfolioRequestRepository : JpaRepository<PortfolioRequest, Long> {
    /** Soft Delete되지 않은 요청만 조회한다. 삭제된 요청은 API에서 존재하지 않는 것으로 취급한다. */
    fun findByIdAndDeletedAtIsNull(id: Long): PortfolioRequest?

    /**
     * 학생 제출(Upsert) 시 요청 Row에 Pessimistic Write Lock을 건다. 같은 요청·학생 조합의 제출물은
     * 하나뿐인데(uk_portfolio_submissions_request_member), 같은 학생이 거의 동시에 두 번 제출하면
     * 둘 다 "기존 제출물 없음"을 보고 각각 INSERT를 시도해 두 번째가 UNIQUE 제약 위반(500)으로 끝난다.
     * 요청 Row를 잠가 같은 요청에 대한 제출을 순차화하면 두 번째 요청은 첫 번째가 만든 Row를 보고
     * 정상 Update 흐름을 탄다(Program 신청의 [ProgramRepository.findByIdForUpdate]와 같은 관례,
     * 최종 방어선은 UNIQUE 제약이다).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM PortfolioRequest r WHERE r.id = :id AND r.deletedAt IS NULL")
    fun findByIdAndDeletedAtIsNullForUpdate(
        @Param("id") id: Long,
    ): PortfolioRequest?

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
