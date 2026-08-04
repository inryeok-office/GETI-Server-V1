package team.inreok.getiserver.domain.collector.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import team.inreok.getiserver.domain.collector.entity.CollectionRun
import team.inreok.getiserver.domain.collector.entity.type.CollectionRunStatus
import java.time.LocalDateTime

interface CollectionRunRepository : JpaRepository<CollectionRun, Long> {
    // 같은 Source에 이미 진행 중인 실행(PENDING/RUNNING)이 있는지 확인한다(COLLECTOR_ALREADY_RUNNING).
    fun existsBySourceIdAndStatusIn(
        sourceId: Long,
        statuses: Collection<CollectionRunStatus>,
    ): Boolean

    // 목록 조회는 DB Pagination과 필터를 함께 처리한다. 모든 조건은 null이면 적용하지 않는다.
    //
    // startAt/endAt은 ":startAt IS NULL OR ..." 형태로 두면 PostgreSQL이 Parameter Type을 추론하지
    // 못해 "could not determine data type of parameter"(CAST 시도 시 "cannot cast type bytea to
    // timestamp")로 실패한다(JobRepository.searchPublic의 LIKE Pattern Type 추론 문제와 같은 종류의
    // PostgreSQL 제약). COALESCE로 같은 Column과 짝지어 Parameter Type을 그 Column에서 추론하게 하면
    // null일 때 자기 자신과 비교해(항상 참) 조건이 사실상 무시되므로 IS NULL 분기 없이 우회한다.
    @Query(
        """
        SELECT r FROM CollectionRun r
        WHERE (:sourceId IS NULL OR r.sourceId = :sourceId)
          AND (:status IS NULL OR r.status = :status)
          AND r.startedAt >= COALESCE(:startAt, r.startedAt)
          AND r.startedAt <= COALESCE(:endAt, r.startedAt)
        """,
    )
    fun search(
        @Param("sourceId") sourceId: Long?,
        @Param("status") status: CollectionRunStatus?,
        @Param("startAt") startAt: LocalDateTime?,
        @Param("endAt") endAt: LocalDateTime?,
        pageable: Pageable,
    ): Page<CollectionRun>
}
