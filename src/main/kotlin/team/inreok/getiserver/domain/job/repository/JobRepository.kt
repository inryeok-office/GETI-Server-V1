package team.inreok.getiserver.domain.job.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.job.entity.Job
import team.inreok.getiserver.domain.job.entity.type.JobStatus
import java.time.LocalDateTime

@Suppress("TooManyFunctions")
interface JobRepository : JpaRepository<Job, Long> {
    fun findBySourceNameAndExternalJobId(
        sourceName: String,
        externalJobId: String,
    ): Job?

    // 삭제된 공고(deletedAt != null)는 조회 대상이 아니다. Soft Delete이므로 findById를 그대로
    // 사용하면 삭제된 공고까지 조회되기 때문에 공개 경로는 항상 이 메서드를 사용한다.
    // 관리자 상세 조회는 삭제 이력까지 확인해야 하므로 findById를 그대로 쓴다.
    fun findByIdAndDeletedAtIsNull(id: Long): Job?

    // findByIdAndDeletedAtIsNull의 배치 버전이다(Issue #136, JobApplicationSnapshotQueryPort.findAllByIds).
    // Recommendation R3(Issue #152, JobRecommendationCandidateQueryPort.findAllByIds)도 같은
    // 메서드를 그대로 재사용한다 -- 두 Issue가 같은 배치 조회(id 목록으로 삭제되지 않은 Job
    // 찾기)가 필요해 PR #151이 먼저 추가한 것을 새로 만들지 않고 그대로 썼다.
    fun findAllByIdInAndDeletedAtIsNull(ids: Collection<Long>): List<Job>

    // Recommendation 후보 조회 전용이다(Issue #148). 현재 규모(활성 공고 약 1,000건)에서는
    // Pagination 없이 한 번에 가져와도 충분하다 — findForReindex처럼 여러 상태를 한꺼번에
    // 받지 않고 PUBLISHED만 조회해, CLOSED까지 가져와 매번 걸러내는 낭비를 피한다.
    fun findAllByStatusAndDeletedAtIsNull(status: JobStatus): List<Job>

    /** 관리자 목록용 원본 DB 조회. 상태 미지정 시 soft-deleted 공고를 제외하고, 상태를 지정하면 해당 상태를 그대로 조회한다. */
    @Query(
        """
        SELECT j FROM Job j
        WHERE (
            (
                :status IS NULL
                AND j.status <> team.inreok.getiserver.domain.job.entity.type.JobStatus.DELETED
                AND j.deletedAt IS NULL
            )
            OR (
                :status IS NOT NULL
                AND j.status = :status
                AND (
                    :status = team.inreok.getiserver.domain.job.entity.type.JobStatus.DELETED
                    OR j.deletedAt IS NULL
                )
            )
        )
          AND (:query IS NULL OR LOWER(j.title) LIKE LOWER(CONCAT('%', CAST(:query AS string), '%')) ESCAPE '\')
        ORDER BY j.createdAt DESC, j.id DESC
        """,
    )
    fun searchForAdmin(
        @Param("query") query: String?,
        @Param("status") status: JobStatus?,
        pageable: Pageable,
    ): Page<Job>

    fun findAllByCompanyIdAndStatusInAndDeletedAtIsNull(
        companyId: Long,
        statuses: Collection<JobStatus>,
    ): List<Job>

    fun findAllByCompanyIdInAndStatusInAndDeletedAtIsNull(
        companyIds: Collection<Long>,
        statuses: Collection<JobStatus>,
    ): List<Job>

    @Query(
        """
        SELECT CASE WHEN COUNT(j.id) > 0 THEN TRUE ELSE FALSE END
        FROM Job j
        WHERE j.companyId = :companyId
          AND j.status = team.inreok.getiserver.domain.job.entity.type.JobStatus.PUBLISHED
          AND j.deletedAt IS NULL
          AND (j.recruitmentEndedAt IS NULL OR j.recruitmentEndedAt >= :now)
        """,
    )
    fun existsActiveByCompanyId(
        @Param("companyId") companyId: Long,
        @Param("now") now: LocalDateTime,
    ): Boolean

    // 공개 목록/검색(GET /api/v1/jobs)은 더 이상 이 Repository를 직접 쓰지 않는다(Issue #69,
    // domain.search.query.JobSearchQueryPort가 Elasticsearch로 대체). 이 Query는 Search의 전체
    // 재색인이 Postgres를 원본으로 다시 읽을 때 쓰는 최소 목적의 재색인용 조회다 — 필터는 없고
    // "공개 대상 여부"와 "id 기준 Keyset Pagination"만 담당한다.
    @Query(
        """
        SELECT j FROM Job j
        WHERE j.deletedAt IS NULL
          AND j.status IN :statuses
          AND j.id > :afterId
        ORDER BY j.id ASC
        """,
    )
    fun findForReindex(
        @Param("statuses") statuses: Collection<JobStatus>,
        @Param("afterId") afterId: Long,
        pageable: Pageable,
    ): Page<Job>

    /**
     * 조회수를 DB에서 원자적으로 증가시킨다. 읽어서 +1 한 뒤 저장하면 동시 요청에서 증가분이
     * 유실되므로 UPDATE 한 번으로 처리한다.
     *
     * `clearAutomatically`/`flushAutomatically`는 켜지 않는다. 켜면 영속성 Context가 비워져
     * 호출 직전에 읽어둔 [Job]이 detach되기 때문이다. 호출 측이 응답을 먼저 만든 뒤 이 메서드를
     * 부르고, 응답의 조회수에는 증가분을 직접 반영한다.
     *
     * `@Transactional`은 기본값(REQUIRED)이라 Service의 Transaction이 있으면 그대로 참여한다.
     * Transaction 없이 호출되는 경우에만 자체 Transaction을 열어 `@Modifying` Query가
     * `TransactionRequiredException`으로 실패하지 않게 한다.
     */
    @Modifying
    @Transactional
    @Query("UPDATE Job j SET j.viewCount = j.viewCount + 1 WHERE j.id = :id")
    fun incrementViewCount(
        @Param("id") id: Long,
    ): Int

    // 관리자 지원자 목록 필터(Issue #181)의 기업·담당자 조건을 만족하는 삭제되지 않은 Job id를
    // 찾는다. JobApplication은 jobId만 가지고 있어 companyId/managerMemberId로 직접 필터링할 수
    // 없으므로(JobApplicationAdminFilterQueryPort KDoc 참고), application 모듈이 이 결과를
    // jobId IN (...) 조건으로 사용한다. 세 조건은 모두 null이면 적용하지 않고(search()와 동일한
    // 관례) AND로 결합한다. :mineOnlyMemberId는 managerMemberId 또는 createdByMemberId 중 하나만
    // 일치해도 포함한다(사용자 확인 완료 -- 공고에 managerMemberId를 지정하는 API가 아직 없어
    // managerMemberId만 기준으로 하면 "담당 공고" 필터가 항상 빈 목록이 되기 때문에 등록자 기준도
    // 포함한다).
    @Query(
        """
        SELECT j.id FROM Job j
        WHERE j.deletedAt IS NULL
          AND (:companyId IS NULL OR j.companyId = :companyId)
          AND (:managerMemberId IS NULL OR j.managerMemberId = :managerMemberId)
          AND (
            :mineOnlyMemberId IS NULL
            OR j.managerMemberId = :mineOnlyMemberId
            OR j.createdByMemberId = :mineOnlyMemberId
          )
        """,
    )
    fun findIdsByCompanyOrManagerFilter(
        @Param("companyId") companyId: Long?,
        @Param("managerMemberId") managerMemberId: Long?,
        @Param("mineOnlyMemberId") mineOnlyMemberId: Long?,
    ): List<Long>

    @Query(
        """
        SELECT j FROM Job j
        WHERE j.deletedAt IS NULL
          AND (j.managerMemberId = :memberId OR j.createdByMemberId = :memberId)
        ORDER BY j.id DESC
        """,
    )
    fun findManagedByMemberId(
        @Param("memberId") memberId: Long,
        pageable: Pageable,
    ): Page<Job>
}
