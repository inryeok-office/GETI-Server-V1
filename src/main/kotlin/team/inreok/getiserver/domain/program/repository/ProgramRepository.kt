package team.inreok.getiserver.domain.program.repository

import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.program.entity.Program
import team.inreok.getiserver.domain.program.entity.type.ProgramStatus
import team.inreok.getiserver.domain.program.entity.type.ProgramType
import java.time.LocalDateTime

interface ProgramRepository : JpaRepository<Program, Long> {
    // 삭제된 Program(deletedAt != null)은 조회 대상이 아니다(Soft Delete). 관리자 상세 조회는
    // 삭제 이력까지 확인해야 하므로 findById를 그대로 쓴다(JobRepository와 동일한 관례).
    fun findByIdAndDeletedAtIsNull(id: Long): Program?

    /**
     * 정원 초과를 막기 위해 Program Row에 Pessimistic Write Lock을 건다(요구사항 11절/22절
     * 동시성). 신청(APPLY)·취소(CANCEL)·정원 수정·상태(삭제) 변경이 모두 이 Method로 조회한
     * 뒤 처리해야 같은 Program에 대한 서로 다른 요청이 순차적으로 실행된다. "조회 → 비교 →
     * Insert" 3단계 분리 방식(TOCTOU 위험)을 피하기 위한 것으로, 최종 방어선은
     * uk_program_applications_active_singleton(V15 Migration, Partial Unique Index)이다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Program p WHERE p.id = :id AND p.deletedAt IS NULL")
    fun findByIdForUpdate(
        @Param("id") id: Long,
    ): Program?

    // :type, :status는 null이면 조건을 적용하지 않는다(FormRepository.search와 동일한 관례).
    // openOnly=true는 PUBLISHED 상태이고 현재 신청 기간(신청 시작<=now<=신청 종료) 안에 있는
    // Program만 반환한다. 정원 마감 여부까지 제외하는지는 노션 계약이 확정되지 않아(원본
    // 요구사항 9절) 포함하지 않았다 — 완료 보고에 DECISION_REQUIRED로 남긴다.
    @Query(
        """
        SELECT p FROM Program p
        WHERE p.deletedAt IS NULL
          AND (:type IS NULL OR p.type = :type)
          AND (:status IS NULL OR p.status = :status)
          AND (:openOnly = FALSE OR (
                p.status = :publishedStatus
                AND p.applicationStartedAt IS NOT NULL AND p.applicationStartedAt <= :now
                AND p.applicationEndedAt IS NOT NULL AND p.applicationEndedAt >= :now
          ))
        ORDER BY p.id DESC
        """,
    )
    fun search(
        @Param("type") type: ProgramType?,
        @Param("status") status: ProgramStatus?,
        @Param("openOnly") openOnly: Boolean,
        @Param("publishedStatus") publishedStatus: ProgramStatus,
        @Param("now") now: LocalDateTime,
        pageable: Pageable,
    ): Page<Program>

    /**
     * 조회수를 DB에서 원자적으로 증가시킨다(JobRepository.incrementViewCount와 동일한 이유).
     */
    @Modifying
    @Transactional
    @Query("UPDATE Program p SET p.viewCount = p.viewCount + 1 WHERE p.id = :id")
    fun incrementViewCount(
        @Param("id") id: Long,
    ): Int
}
