package team.inreok.getiserver.domain.search.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import team.inreok.getiserver.domain.search.entity.SearchIndexFailure
import java.time.LocalDateTime

interface SearchIndexFailureRepository : JpaRepository<SearchIndexFailure, Long> {
    fun findByJobId(jobId: Long): SearchIndexFailure?

    fun deleteByJobId(jobId: Long)

    // next_retry_at이 설정되어 있고 그 시각이 지난 Row만 대상이다. next_retry_at이 NULL인 Row
    // (최대 재시도 초과, 영구 실패)는 대상에서 제외된다.
    @Query(
        """
        SELECT f FROM SearchIndexFailure f
        WHERE f.nextRetryAt IS NOT NULL AND f.nextRetryAt <= :now
        ORDER BY f.id ASC
        """,
    )
    fun findDueForRetry(
        @Param("now") now: LocalDateTime,
    ): List<SearchIndexFailure>
}
