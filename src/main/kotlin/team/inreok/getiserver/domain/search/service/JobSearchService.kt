package team.inreok.getiserver.domain.search.service

import org.springframework.data.domain.Pageable
import team.inreok.getiserver.domain.ai.entity.type.AiDifficulty
import team.inreok.getiserver.domain.ai.entity.type.AiFitLevel
import team.inreok.getiserver.domain.company.entity.type.CompanyType
import team.inreok.getiserver.domain.job.entity.type.ApplicationMethod
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
    /**
     * [requesterId]는 검색 결과 각 항목의 `company.logoUrl`을 발급할 때 쓰는 요청자 ID다
     * (Issue #92, `FileUrlPort.presignedImageUrls`). 로고는 기업 공개 정보라 어떤 인증된
     * 사용자를 넘겨도 판정 결과는 같지만(`CompanyLogoAccessChecker`), Port 자체가 요청자
     * 없는 호출을 허용하지 않는다.
     */
    @Suppress("LongParameterList")
    fun search(
        query: String?,
        postingType: PostingType?,
        applicationMethod: ApplicationMethod?,
        status: PublicJobStatus?,
        companyType: CompanyType?,
        sourceName: String?,
        targetGrade: Int?,
        highSchoolGraduateFit: AiFitLevel?,
        entryLevelFit: AiFitLevel?,
        difficulty: AiDifficulty?,
        openOnly: Boolean,
        sort: JobSort,
        direction: SortDirection?,
        pageable: Pageable,
        requesterId: Long,
    ): JobSearchResponse
}
