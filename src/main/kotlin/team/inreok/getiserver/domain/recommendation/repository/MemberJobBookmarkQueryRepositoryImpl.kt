package team.inreok.getiserver.domain.recommendation.repository

import jakarta.persistence.EntityManager
import jakarta.persistence.Query
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Repository
import team.inreok.getiserver.domain.job.query.JobBookmarkQuery
import team.inreok.getiserver.domain.job.query.JobBookmarkSort

/** Preference Table 소유 Module에서 북마크 조건과 Job 검색 조건을 DB Query로 조합한다. */
@Repository
class MemberJobBookmarkQueryRepositoryImpl(
    private val entityManager: EntityManager,
) : MemberJobBookmarkQueryRepository {
    override fun findBookmarkedJobIdsByMemberIdAndQuery(
        memberId: Long,
        query: JobBookmarkQuery,
        pageable: Pageable,
    ): Page<Long> {
        val normalizedQuery = query.query?.trim()?.takeIf { it.isNotEmpty() }
        val conditions =
            mutableListOf(
                "p.member_id = :memberId",
                "p.bookmarked = TRUE",
                "j.deleted_at IS NULL",
                "j.status IN ('PUBLISHED', 'CLOSED')",
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
            "FROM member_job_preferences p " +
                "JOIN jobs j ON j.id = p.job_id " +
                "LEFT JOIN companies c ON c.id = j.company_id AND c.deleted_at IS NULL " +
                "WHERE ${conditions.joinToString(" AND ")}"
        val dataQuery =
            entityManager.createNativeQuery(
                "SELECT p.job_id $fromAndWhere ORDER BY ${orderBy(query.sort)}",
            )
        val countQuery = entityManager.createNativeQuery("SELECT COUNT(p.job_id) $fromAndWhere")

        bindParameters(dataQuery, memberId, normalizedQuery, query)
        bindParameters(countQuery, memberId, normalizedQuery, query)

        if (pageable.isPaged) {
            dataQuery.firstResult = pageable.offset.toInt()
            dataQuery.maxResults = pageable.pageSize
        }

        val content = dataQuery.resultList.map { (it as Number).toLong() }
        val total = (countQuery.singleResult as Number).toLong()
        return PageImpl(content, pageable, total)
    }

    private fun bindParameters(
        query: Query,
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
