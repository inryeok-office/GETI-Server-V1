package team.inreok.getiserver.domain.job.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import team.inreok.getiserver.domain.job.entity.Job
import team.inreok.getiserver.domain.job.query.JobBookmarkQuery

/** 북마크 조건과 공개 공고 조건을 한 DB Query에서 함께 적용하는 Job Repository Fragment다. */
interface JobBookmarkQueryRepository {
    fun findBookmarkedByMemberId(
        memberId: Long,
        query: JobBookmarkQuery,
        pageable: Pageable,
    ): Page<Job>
}
