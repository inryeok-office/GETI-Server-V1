package team.inreok.getiserver.domain.job.repository

import jakarta.persistence.EntityManager
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Repository
import team.inreok.getiserver.domain.job.entity.Job
import team.inreok.getiserver.domain.job.query.JobBookmarkQuery
import team.inreok.getiserver.domain.job.query.JobBookmarkSort

/**
 * Job와 MemberJobPreference를 DB에서 함께 필터링한다.
 *
 * Recommendation Module이 Job Entity/Repository를 직접 참조하지 않도록 이 Query는 Job Module이
 * 소유하고, 다른 Module에는 [team.inreok.getiserver.domain.job.query.JobRecommendationCandidateQueryPort]
 * 로만 공개한다. 정렬 SQL은 외부 입력이 아니라 제한된 Enum에서만 선택한다.
 */
@Repository
class JobBookmarkQueryRepositoryImpl(
    private val entityManager: EntityManager,
) : JobBookmarkQueryRepository {
    override fun findBookmarkedByMemberId(
        memberId: Long,
        query: JobBookmarkQuery,
        pageable: Pageable,
    ): Page<Job> {
        val normalizedQuery = query.query?.trim()?.takeIf { it.isNotEmpty() }
        val conditions =
            mutableListOf(
                "j.deleted_at IS NULL",
                "j.status IN ('PUBLISHED', 'CLOSED')",
                "EXISTS (" +
                    "SELECT 1 FROM member_job_preferences p " +
                    "WHERE p.job_id = j.id AND p.member_id = :memberId AND p.bookmarked = TRUE" +
                    ")",
            )
        if (normalizedQuery != null) {
            conditions +=
                "(" +
                "LOWER(j.title) LIKE :queryPattern ESCAPE '\\' " +
                "OR LOWER(c.name) LIKE :queryPattern ESCAPE '\\' " +
                "OR LOWER(COALESCE(j.body_markdown, '')) LIKE :queryPattern ESCAPE '\\'" +
                ")"
        }
        if (query.postingType != null) conditions += "j.type = :postingType"
        if (query.companyType != null) conditions += "c.type = :companyType"

        val fromAndWhere =
            "FROM jobs j " +
                "LEFT JOIN companies c ON c.id = j.company_id AND c.deleted_at IS NULL " +
                "WHERE ${conditions.joinToString(" AND ")}"
        val dataQuery =
            entityManager.createNativeQuery(
                "SELECT j.* $fromAndWhere ORDER BY ${orderBy(query.sort)}",
                Job::class.java,
            )
        val countQuery = entityManager.createNativeQuery("SELECT COUNT(j.id) $fromAndWhere")

        bindParameters(dataQuery, memberId, normalizedQuery, query)
        bindParameters(countQuery, memberId, normalizedQuery, query)

        if (pageable.isPaged) {
            dataQuery.firstResult = pageable.offset.toInt()
            dataQuery.maxResults = pageable.pageSize
        }

        @Suppress("UNCHECKED_CAST")
        val content = dataQuery.resultList as List<Job>
        val total = (countQuery.singleResult as Number).toLong()
        return PageImpl(content, pageable, total)
    }

    private fun bindParameters(
        query: jakarta.persistence.Query,
        memberId: Long,
        normalizedQuery: String?,
        filter: JobBookmarkQuery,
    ) {
        query.setParameter("memberId", memberId)
        normalizedQuery?.let { query.setParameter("queryPattern", "%${escapeLikePattern(it)}%".lowercase()) }
        filter.postingType?.let { query.setParameter("postingType", it.name) }
        filter.companyType?.let { query.setParameter("companyType", it) }
    }

    private fun orderBy(sort: JobBookmarkSort): String =
        when (sort) {
            JobBookmarkSort.LATEST -> "j.published_at DESC NULLS LAST, j.id DESC"
            JobBookmarkSort.DEADLINE -> "j.recruitment_ended_at ASC NULLS LAST, j.id DESC"
            JobBookmarkSort.VIEWS -> "j.view_count DESC, j.id DESC"
        }

    private fun escapeLikePattern(value: String): String =
        value
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
}
