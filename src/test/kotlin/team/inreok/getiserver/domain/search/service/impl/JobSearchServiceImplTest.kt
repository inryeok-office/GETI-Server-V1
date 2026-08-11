package team.inreok.getiserver.domain.search.service.impl

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyCollection
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.data.domain.PageRequest
import org.springframework.data.elasticsearch.client.elc.NativeQuery
import org.springframework.data.elasticsearch.core.SearchHit
import org.springframework.data.elasticsearch.core.SearchHits
import org.springframework.data.elasticsearch.core.query.Query
import team.inreok.getiserver.domain.file.link.FileUrlPort
import team.inreok.getiserver.domain.search.document.JobSearchDocument
import team.inreok.getiserver.domain.search.dto.JobSort
import team.inreok.getiserver.domain.search.dto.PublicJobStatus
import team.inreok.getiserver.domain.search.index.JobSearchIndexManager
import java.time.LocalDateTime

/**
 * `JobSearchServiceImpl.search`가 목록 응답의 `company.logoUrl`을 배치로 발급하는지 검증한다
 * (Issue #92). Elasticsearch 자체는 [JobSearchIndexManager]를 Mock으로 대체해 실제로 붙지 않는다
 * (Testcontainers 기반 통합 검증은 `JobSearchElasticsearchIntegrationTest` 참고).
 */
@ExtendWith(MockitoExtension::class)
class JobSearchServiceImplTest {
    @Mock
    private lateinit var indexManager: JobSearchIndexManager

    @Mock
    private lateinit var fileUrlPort: FileUrlPort

    @Mock
    private lateinit var searchHits: SearchHits<JobSearchDocument>

    private val service: JobSearchServiceImpl by lazy { JobSearchServiceImpl(indexManager, fileUrlPort) }

    @Test
    fun `여러 건의 결과가 있어도 로고 URL은 한 번의 배치 호출로 발급한다`() {
        val hits =
            listOf(
                hitOf(documentOf(jobId = 1L, companyLogoFileId = 10L)),
                hitOf(documentOf(jobId = 2L, companyLogoFileId = 20L)),
                // 같은 기업의 공고가 여러 건이면 File ID가 중복될 수 있다 -- 중복 제거 확인.
                hitOf(documentOf(jobId = 3L, companyLogoFileId = 10L)),
            )
        given(searchHits.searchHits).willReturn(hits)
        given(searchHits.totalHits).willReturn(3L)
        given(indexManager.search(anyQuery())).willReturn(searchHits)
        given(fileUrlPort.presignedImageUrls(REQUESTER_ID, listOf(10L, 20L)))
            .willReturn(mapOf(10L to "https://storage.example/logo-10", 20L to "https://storage.example/logo-20"))

        val response = search()

        assertThat(response.content).hasSize(3)
        assertThat(response.content[0].company?.logoUrl).isEqualTo("https://storage.example/logo-10")
        assertThat(response.content[1].company?.logoUrl).isEqualTo("https://storage.example/logo-20")
        assertThat(response.content[2].company?.logoUrl).isEqualTo("https://storage.example/logo-10")
        // 공고마다 단건 발급하면 목록 크기만큼 반복된다(N+1). 정확히 한 번이어야 한다.
        verify(fileUrlPort, times(1)).presignedImageUrls(REQUESTER_ID, listOf(10L, 20L))
    }

    @Test
    fun `기업에 로고가 없으면 logoUrl은 null이고 File Port를 호출하지 않는다`() {
        given(searchHits.searchHits).willReturn(listOf(hitOf(documentOf(jobId = 1L, companyLogoFileId = null))))
        given(searchHits.totalHits).willReturn(1L)
        given(indexManager.search(anyQuery())).willReturn(searchHits)

        val response = search()

        assertThat(response.content[0].company?.logoUrl).isNull()
        verify(fileUrlPort, never()).presignedImageUrls(anyLong(), anyCollection())
    }

    @Test
    fun `기업이 삭제되어 company 자체가 없으면 logoUrl도 없다`() {
        given(searchHits.searchHits)
            .willReturn(listOf(hitOf(documentOf(jobId = 1L, companyName = null, companyLogoFileId = null))))
        given(searchHits.totalHits).willReturn(1L)
        given(indexManager.search(anyQuery())).willReturn(searchHits)

        val response = search()

        assertThat(response.content[0].company).isNull()
    }

    // --- Fixture ---
    //
    // Query(비Null 파라미터)에 bare any()를 쓰면 Kotlin의 Null 검사가 걸려 NPE가 나므로 Elvis로
    // 기본값을 준다(JobSearchControllerTest.anySort/anyPageable와 같은 이유).
    private fun anyQuery(): Query = any(Query::class.java) ?: NativeQuery.builder().build()

    private fun search() =
        service.search(
            query = null,
            postingType = null,
            status = null,
            companyType = null,
            sourceName = null,
            targetGrade = null,
            openOnly = false,
            sort = JobSort.LATEST,
            direction = null,
            pageable = PageRequest.of(0, 20),
            requesterId = REQUESTER_ID,
        )

    private fun hitOf(document: JobSearchDocument): SearchHit<JobSearchDocument> =
        SearchHit(
            "jobs-search",
            document.id,
            null,
            1.0f,
            null,
            emptyMap(),
            emptyMap(),
            null,
            null,
            emptyMap(),
            document,
        )

    private fun documentOf(
        jobId: Long,
        companyName: String? = "인력개발원",
        companyLogoFileId: Long?,
    ) = JobSearchDocument(
        id = jobId.toString(),
        jobId = jobId,
        title = "2026 상반기 백엔드 채용",
        content = null,
        postingType = "MOU",
        applicationMethod = "EXTERNAL",
        status = PublicJobStatus.PUBLISHED.jobStatus.name,
        companyId = 1L,
        companyName = companyName,
        companyType = "GENERAL",
        companyLogoFileId = companyLogoFileId,
        sourceName = null,
        targetGrade = null,
        capacity = null,
        firstComeServed = false,
        viewCount = 0,
        publishedAt = LocalDateTime.now(),
        startDate = null,
        endDate = null,
    )

    private companion object {
        private const val REQUESTER_ID = 1L
    }
}
