package team.inreok.getiserver.domain.search.service

import org.springframework.data.domain.Pageable
import team.inreok.getiserver.domain.company.entity.type.CompanyType
import team.inreok.getiserver.domain.job.entity.type.PostingType
import team.inreok.getiserver.domain.search.dto.JobSearchResponse
import team.inreok.getiserver.domain.search.dto.JobSort
import team.inreok.getiserver.domain.search.dto.PublicJobStatus
import team.inreok.getiserver.domain.search.dto.SortDirection

/**
 * `GET /api/v1/jobs`(공개 공고 검색)의 실제 구현이다(Issue #69). Elasticsearch가 검색 전용
 * Read Model이므로 이 결과는 PostgreSQL을 다시 조회하지 않고 그대로 응답으로 옮길 수 있다.
 */
interface JobSearchService {
    @Suppress("LongParameterList")
    fun search(
        query: String?,
        postingType: PostingType?,
        status: PublicJobStatus?,
        companyType: CompanyType?,
        sourceName: String?,
        targetGrade: Int?,
        openOnly: Boolean,
        sort: JobSort,
        direction: SortDirection?,
        pageable: Pageable,
    ): JobSearchResponse
}
