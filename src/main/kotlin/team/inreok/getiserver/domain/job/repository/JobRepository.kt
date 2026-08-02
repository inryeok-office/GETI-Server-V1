package team.inreok.getiserver.domain.job.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import team.inreok.getiserver.domain.job.entity.Job
import team.inreok.getiserver.domain.job.entity.type.JobStatus
import team.inreok.getiserver.domain.job.entity.type.PostingType
import java.time.LocalDateTime

interface JobRepository : JpaRepository<Job, Long> {
    fun findBySourceNameAndExternalJobId(
        sourceName: String,
        externalJobId: String,
    ): Job?

    // 삭제된 공고(deletedAt != null)는 조회 대상이 아니다. Soft Delete이므로 findById를 그대로
    // 사용하면 삭제된 공고까지 조회되기 때문에 공개 경로는 항상 이 메서드를 사용한다.
    // 관리자 상세 조회는 삭제 이력까지 확인해야 하므로 findById를 그대로 쓴다.
    fun findByIdAndDeletedAtIsNull(id: Long): Job?

    // :query는 Service 계층에서 LIKE Wildcard(%, _)와 Escape 문자(\)를 미리 이스케이프해 전달한다
    // (domain.job.service.escapeLikePattern 참고). null이면 제목 조건을 적용하지 않는다.
    // 기업명은 검색 대상이 아니다 — companies Table을 Join하면 Company Module 내부 구현에
    // 의존하게 되어 ModularityTest가 실패한다(Issue #60 제외 범위).
    @Query(
        """
        SELECT j FROM Job j
        WHERE j.deletedAt IS NULL
          AND j.status IN :statuses
          AND (:query IS NULL OR LOWER(j.title) LIKE LOWER(CONCAT('%', :query, '%')) ESCAPE '\')
          AND (:postingType IS NULL OR j.type = :postingType)
          AND (
                :openOnly = FALSE
                OR (
                    j.status = team.inreok.getiserver.domain.job.entity.type.JobStatus.PUBLISHED
                    AND (j.recruitmentEndedAt IS NULL OR j.recruitmentEndedAt > :now)
                )
              )
        """,
    )
    fun searchPublic(
        @Param("statuses") statuses: Collection<JobStatus>,
        @Param("query") query: String?,
        @Param("postingType") postingType: PostingType?,
        @Param("openOnly") openOnly: Boolean,
        @Param("now") now: LocalDateTime,
        pageable: Pageable,
    ): Page<Job>

    /**
     * 조회수를 DB에서 원자적으로 증가시킨다. 읽어서 +1 한 뒤 저장하면 동시 요청에서 증가분이
     * 유실되므로 UPDATE 한 번으로 처리한다.
     *
     * `clearAutomatically`/`flushAutomatically`는 켜지 않는다. 켜면 영속성 Context가 비워져
     * 호출 직전에 읽어둔 [Job]이 detach되기 때문이다. 호출 측이 응답을 먼저 만든 뒤 이 메서드를
     * 부르고, 응답의 조회수에는 증가분을 직접 반영한다.
     */
    @Modifying
    @Query("UPDATE Job j SET j.viewCount = j.viewCount + 1 WHERE j.id = :id")
    fun incrementViewCount(
        @Param("id") id: Long,
    ): Int
}
