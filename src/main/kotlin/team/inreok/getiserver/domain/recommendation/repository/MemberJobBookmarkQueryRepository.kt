package team.inreok.getiserver.domain.recommendation.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import team.inreok.getiserver.domain.job.query.JobBookmarkQuery

/** 북마크 Preference와 공고 필터를 DB에서 함께 적용하는 Recommendation 전용 Query Fragment. */
interface MemberJobBookmarkQueryRepository {
    fun findBookmarkedJobIdsByMemberIdAndQuery(
        memberId: Long,
        query: JobBookmarkQuery,
        pageable: Pageable,
    ): Page<Long>
}
