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
    fun findAllByIdInAndDeletedAtIsNull(ids: Collection<Long>): List<Job>

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
}
