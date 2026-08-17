package team.inreok.getiserver.domain.search.service.impl

import co.elastic.clients.elasticsearch._types.FieldValue
import co.elastic.clients.elasticsearch._types.SortOrder
import co.elastic.clients.elasticsearch._types.query_dsl.Query
import org.springframework.data.domain.Pageable
import org.springframework.data.elasticsearch.client.elc.NativeQuery
import org.springframework.stereotype.Service
import team.inreok.getiserver.domain.company.entity.type.CompanyType
import team.inreok.getiserver.domain.file.link.FileUrlPort
import team.inreok.getiserver.domain.job.access.JobApplicationEligibilityAccessSnapshot
import team.inreok.getiserver.domain.job.access.JobApplicationEligibilityAccessor
import team.inreok.getiserver.domain.job.entity.type.PostingType
import team.inreok.getiserver.domain.search.document.JobSearchDocument
import team.inreok.getiserver.domain.search.dto.JobSearchResponse
import team.inreok.getiserver.domain.search.dto.JobSort
import team.inreok.getiserver.domain.search.dto.JobSummaryResponse
import team.inreok.getiserver.domain.search.dto.PublicJobStatus
import team.inreok.getiserver.domain.search.dto.SortDirection
import team.inreok.getiserver.domain.search.index.JobSearchIndexManager
import team.inreok.getiserver.domain.search.service.JobSearchService
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** [JobSearchService]의 구현이다(Issue #69). */
@Service
class JobSearchServiceImpl(
    private val indexManager: JobSearchIndexManager,
    private val fileUrlPort: FileUrlPort,
    private val jobApplicationEligibilityAccessor: JobApplicationEligibilityAccessor,
) : JobSearchService {
    @Suppress("LongParameterList")
    override fun search(
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
        requesterId: Long,
    ): JobSearchResponse {
        // 검색어를 보내지 않은 경우와 공백만 보낸 경우를 모두 "검색어 없음"으로 취급한다.
        val keyword = query?.trim()?.takeIf { it.isNotEmpty() }
        val statuses = status?.let { listOf(it.jobStatus.name) } ?: PublicJobStatus.ALL_VISIBLE.map { it.name }
        val trimmedSourceName = sourceName?.trim()?.takeIf { it.isNotEmpty() }
        val now = LocalDateTime.now()

        val esQuery =
            NativeQuery
                .builder()
                .withQuery(
                    buildQuery(
                        keyword,
                        statuses,
                        postingType,
                        companyType,
                        trimmedSourceName,
                        targetGrade,
                        openOnly,
                        now,
                    ),
                ).withPageable(pageable)
                .withSort { s -> s.field { f -> f.field(sortField(sort)).order(sortOrder(sort, direction)) } }
                .withSort { s -> s.field { f -> f.field("jobId").order(SortOrder.Desc) } }
                // Elasticsearch는 기본적으로 10,000건 이상은 totalHits를 정확히 세지 않고
                // 근사값(하한)으로 반환한다 — Pagination(totalPages)이 부정확해질 수 있어
                // 명시적으로 정확한 count를 요청한다(PR #70 Review 반영).
                .withTrackTotalHits(true)
                .build()

        val hits = indexManager.search(esQuery)
        val size = if (pageable.isPaged) pageable.pageSize else DEFAULT_PAGE_SIZE
        val page = if (pageable.isPaged) pageable.pageNumber else 0
        val totalElements = hits.totalHits
        val totalPages = if (size == 0) 0 else ((totalElements + size - 1) / size).toInt()

        val documents = hits.searchHits.map { it.content }
        val logoUrls = resolveLogoUrls(documents, requesterId)
        val eligibilities = resolveEligibilities(documents, requesterId)

        return JobSearchResponse(
            content = documents.map { JobSummaryResponse.from(it, logoUrls, eligibilities.getValue(it.jobId)) },
            page = page,
            size = size,
            totalElements = totalElements,
            totalPages = totalPages,
            first = page == 0,
            last = page >= totalPages - 1,
        )
    }

    /**
     * 이번 Page에 담긴 `companyLogoFileId`를 모아 한 번에 URL로 바꾼다. 항목마다 단건 발급하면
     * Page 크기만큼 반복된다(N+1, Issue #92 — `CompanyServiceImpl.search`와 같은 이유).
     * Elasticsearch에는 URL 자체를 저장하지 않으므로(`JobSearchDocument.companyLogoFileId`
     * 참고) 이 변환은 매 요청마다 새로 일어난다.
     */
    private fun resolveLogoUrls(
        documents: List<JobSearchDocument>,
        requesterId: Long,
    ): Map<Long, String> {
        val logoFileIds = documents.mapNotNull { it.companyLogoFileId }.distinct()
        if (logoFileIds.isEmpty()) return emptyMap()
        return fileUrlPort.presignedImageUrls(requesterId, logoFileIds)
    }

    /**
     * 이번 Page에 담긴 공고 전체의 요청자 기준 지원 가능 여부·지원 현황을 한 번에 계산한다(Issue
     * #136, [resolveLogoUrls]와 같은 이유 -- N+1 방지). [JobApplicationEligibilityAccessor]는
     * `job`이 소유하지만 `search`도 그대로 소비한다(Interface Class 주석 참고).
     */
    private fun resolveEligibilities(
        documents: List<JobSearchDocument>,
        requesterId: Long,
    ): Map<Long, JobApplicationEligibilityAccessSnapshot> {
        val jobIds = documents.map { it.jobId }.toSet()
        if (jobIds.isEmpty()) return emptyMap()
        return jobApplicationEligibilityAccessor.findAllByJobIds(jobIds, requesterId)
    }

    @Suppress("LongParameterList")
    private fun buildQuery(
        keyword: String?,
        statuses: Collection<String>,
        postingType: PostingType?,
        companyType: CompanyType?,
        sourceName: String?,
        targetGrade: Int?,
        openOnly: Boolean,
        now: LocalDateTime,
    ): Query =
        Query.of { q ->
            q.bool { b ->
                b.filter { f ->
                    f.terms { t -> t.field("status").terms { v -> v.value(statuses.map(FieldValue::of)) } }
                }
                postingType?.let { b.filter { f -> f.term { t -> t.field("postingType").value(it.name) } } }
                companyType?.let { b.filter { f -> f.term { t -> t.field("companyType").value(it.name) } } }
                sourceName?.let { b.filter { f -> f.term { t -> t.field("sourceName").value(it) } } }
                targetGrade?.let { b.filter { f -> f.term { t -> t.field("targetGrade").value(it.toLong()) } } }
                if (openOnly) {
                    b.filter { f -> f.term { t -> t.field("status").value(PublicJobStatus.PUBLISHED.jobStatus.name) } }
                    b.filter { f ->
                        f.bool { openBool ->
                            openBool.should { s ->
                                s.bool { nb -> nb.mustNot { mn -> mn.exists { e -> e.field("endDate") } } }
                            }
                            openBool.should { s ->
                                s.range { r -> r.date { d -> d.field("endDate").gt(NOW_FORMATTER.format(now)) } }
                            }
                            openBool.minimumShouldMatch("1")
                        }
                    }
                }
                keyword?.let { kw ->
                    b.must { m -> m.multiMatch { mm -> mm.query(kw).fields("title^3", "companyName^2", "content") } }
                }
                b
            }
        }

    private fun sortField(sort: JobSort): String =
        when (sort) {
            JobSort.LATEST -> "publishedAt"
            JobSort.DEADLINE -> "endDate"
            JobSort.VIEWS -> "viewCount"
        }

    // direction을 지정하지 않으면 각 정렬 기준의 기본 방향을 쓴다(기능명세서의 "오름차순·내림차순"
    // 요구사항, Issue #69). 보조 정렬(jobId)의 방향은 이 값과 무관하게 항상 DESC로 고정한다.
    private fun sortOrder(
        sort: JobSort,
        direction: SortDirection?,
    ): SortOrder {
        if (direction != null) return if (direction == SortDirection.ASC) SortOrder.Asc else SortOrder.Desc
        return when (sort) {
            JobSort.LATEST -> SortOrder.Desc
            JobSort.DEADLINE -> SortOrder.Asc
            JobSort.VIEWS -> SortOrder.Desc
        }
    }

    companion object {
        private const val DEFAULT_PAGE_SIZE = 20

        // JobSearchDocument.endDate의 Elasticsearch Mapping Format(date_hour_minute_second_millis,
        // "yyyy-MM-dd'T'HH:mm:ss.SSS")과 정확히 일치해야 한다. ISO_LOCAL_DATE_TIME은 밀리초
        // 자릿수가 가변적이라(0~9자리) Format이 맞지 않으면 Elasticsearch가 Range Query의 날짜
        // 값을 파싱하지 못해 검색 전체가 실패한다(실제 Elasticsearch Integration Test로 확인함).
        private val NOW_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS")
    }
}
