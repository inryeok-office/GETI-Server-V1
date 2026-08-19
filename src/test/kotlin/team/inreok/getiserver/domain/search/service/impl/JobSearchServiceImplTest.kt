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
import team.inreok.getiserver.domain.job.access.JobApplicationEligibilityAccessSnapshot
import team.inreok.getiserver.domain.job.access.JobApplicationEligibilityAccessor
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

    @Mock
    private lateinit var jobApplicationEligibilityAccessor: JobApplicationEligibilityAccessor

    private val service: JobSearchServiceImpl by lazy {
        // 이 Test의 관심사는 logoUrl 배치 발급(Issue #92)이지 지원 가능 여부 계산(Issue #136)이
        // 아니므로, 요청된 jobId 전체에 기본값을 채워주는 Stub 하나를 모든 Test가 공유한다.
        given(
            jobApplicationEligibilityAccessor.findAllByJobIds(any() ?: emptySet(), anyLong()),
        ).willAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val jobIds = invocation.arguments[0] as Set<Long>
            jobIds.associateWith { defaultApplicationEligibility() }
        }
        JobSearchServiceImpl(indexManager, fileUrlPort, jobApplicationEligibilityAccessor)
    }

    private fun defaultApplicationEligibility() =
        JobApplicationEligibilityAccessSnapshot(
            canApply = true,
            eligibilityReason = "AVAILABLE",
            eligibilityMessage = "지원 가능한 공고입니다.",
            applicationId = null,
            applicationStatus = null,
            availableActions = listOf("CREATE_DRAFT"),
        )

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
    fun `목록 항목마다 요청자 기준 지원 가능 여부를 한 번의 배치 호출로 채운다`() {
        val hits =
            listOf(
                hitOf(documentOf(jobId = 1L, companyLogoFileId = null)),
                hitOf(documentOf(jobId = 2L, companyLogoFileId = null)),
            )
        given(searchHits.searchHits).willReturn(hits)
        given(searchHits.totalHits).willReturn(2L)
        given(indexManager.search(anyQuery())).willReturn(searchHits)
        val eligibility1 = defaultApplicationEligibility()
        val eligibility2 =
            JobApplicationEligibilityAccessSnapshot(
                canApply = false,
                eligibilityReason = "ALREADY_APPLIED",
                eligibilityMessage = "이미 이 공고에 지원한 이력이 있습니다.",
                applicationId = 42L,
                applicationStatus = "SUBMITTED",
                availableActions = listOf("REQUEST_EDIT", "WITHDRAW"),
            )
        // service를 먼저 참조해 기본 Stub을 등록시킨 뒤 이 Test만의 값으로 재정의한다
        // (JobServiceTest의 같은 이유).
        service
        given(jobApplicationEligibilityAccessor.findAllByJobIds(setOf(1L, 2L), REQUESTER_ID))
            .willReturn(mapOf(1L to eligibility1, 2L to eligibility2))

        val response = search()

        assertThat(response.content[0].application).isEqualTo(eligibility1)
        assertThat(response.content[1].application).isEqualTo(eligibility2)
        // 공고마다 단건 조회하면 목록 크기만큼 반복된다(N+1). 정확히 한 번이어야 한다.
        verify(jobApplicationEligibilityAccessor, times(1)).findAllByJobIds(setOf(1L, 2L), REQUESTER_ID)
    }

    // Elasticsearch Document의 근무지역·고용형태는 PostgreSQL을 다시 조회하지 않고 그대로
    // 응답으로 옮긴다(Issue #69의 Read Model 원칙, Issue #169).
    @Test
    fun `검색 결과의 근무지역과 고용형태를 목록 응답에 그대로 옮긴다`() {
        given(searchHits.searchHits).willReturn(listOf(hitOf(documentOf(jobId = 1L, companyLogoFileId = null))))
        given(searchHits.totalHits).willReturn(1L)
        given(indexManager.search(anyQuery())).willReturn(searchHits)

        val response = search()

        assertThat(response.content[0].location).isEqualTo("서울특별시 중구")
        assertThat(response.content[0].employmentType).isEqualTo("인턴")
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
            highSchoolGraduateFit = null,
            entryLevelFit = null,
            difficulty = null,
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
        location = "서울특별시 중구",
        employmentType = "인턴",
    )

    private companion object {
        private const val REQUESTER_ID = 1L
    }
}
