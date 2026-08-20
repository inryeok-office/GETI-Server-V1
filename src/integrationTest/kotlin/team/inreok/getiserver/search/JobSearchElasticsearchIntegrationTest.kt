package team.inreok.getiserver.search

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.elasticsearch.test.autoconfigure.DataElasticsearchTest
import org.springframework.data.domain.PageRequest
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.images.builder.ImageFromDockerfile
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import team.inreok.getiserver.domain.ai.entity.type.AiDifficulty
import team.inreok.getiserver.domain.ai.entity.type.AiFitLevel
import team.inreok.getiserver.domain.company.entity.type.CompanyType
import team.inreok.getiserver.domain.file.link.FileUrlPort
import team.inreok.getiserver.domain.job.access.JobApplicationEligibilityAccessSnapshot
import team.inreok.getiserver.domain.job.access.JobApplicationEligibilityAccessor
import team.inreok.getiserver.domain.job.access.JobBookmarkAccessor
import team.inreok.getiserver.domain.job.entity.type.ApplicationMethod
import team.inreok.getiserver.domain.job.entity.type.JobStatus
import team.inreok.getiserver.domain.job.entity.type.PostingType
import team.inreok.getiserver.domain.search.config.SearchProperties
import team.inreok.getiserver.domain.search.document.JobSearchDocument
import team.inreok.getiserver.domain.search.dto.JobSort
import team.inreok.getiserver.domain.search.dto.PublicJobStatus
import team.inreok.getiserver.domain.search.dto.SortDirection
import team.inreok.getiserver.domain.search.index.JobSearchIndexManager
import team.inreok.getiserver.domain.search.service.impl.JobSearchServiceImpl
import java.nio.file.Paths
import java.time.LocalDateTime

/**
 * Elasticsearch/Nori 관련 동작(형태소 검색, 필터 조합, 정렬, Bulk 색인, 삭제)이 실제
 * Elasticsearch에서 의도대로 동작하는지 검증한다(Issue #69). Mock Elasticsearch Client로는
 * Query DSL이 실제로 올바른지 확인할 수 없어 Testcontainers로 실제 Cluster를 띄운다.
 *
 * `docker/elasticsearch/Dockerfile`(공식 Image + Nori Plugin)로 직접 Build한다 — 공식
 * Elasticsearch Image에는 Nori가 기본 포함되지 않는다(compose.yaml과 동일한 이유).
 */
@Testcontainers
@DataElasticsearchTest
class JobSearchElasticsearchIntegrationTest {
    @Autowired
    private lateinit var elasticsearchOperations: ElasticsearchOperations

    private val properties = SearchProperties(indexAlias = TEST_INDEX, indexPrefix = TEST_INDEX)
    private lateinit var indexManager: JobSearchIndexManager
    private lateinit var searchService: JobSearchServiceImpl

    @BeforeEach
    fun setUp() {
        indexManager = JobSearchIndexManager(elasticsearchOperations, properties)
        // 이 Test는 Elasticsearch Query 자체의 정확성만 다룬다 -- 로고 URL 발급(FileUrlPort),
        // 지원 가능 여부 계산(JobApplicationEligibilityAccessor, Issue #136), 북마크 여부
        // 계산(JobBookmarkAccessor, Issue #171)은 각각 JobSearchServiceImplTest(Unit
        // Test)/JobApplicationEligibilityAccessorImplTest/JobBookmarkAccessorImplTest가 이미
        // 검증하므로 여기서는 최소 구현으로 대체한다.
        searchService =
            JobSearchServiceImpl(
                indexManager,
                NoOpFileUrlPort,
                NoOpJobApplicationEligibilityAccessor,
                NoOpJobBookmarkAccessor,
            )

        // 재색인(Alias 전환) 흐름은 SearchReindexServiceImplTest(Unit Test)가 이미 검증하므로,
        // 여기서는 검색 Query 자체의 정확성만 보기 위해 Alias 이름과 동일한 물리 Index를 직접
        // 만든다(Elasticsearch는 Alias와 실제 Index 이름을 같은 이름공간에서 다룬다).
        val indexOps = elasticsearchOperations.indexOps(IndexCoordinates.of(TEST_INDEX))
        if (indexOps.exists()) indexOps.delete()
        indexOps.create(indexOps.createSettings(JobSearchDocument::class.java))
        indexOps.putMapping(indexOps.createMapping(JobSearchDocument::class.java))
    }

    @AfterEach
    fun tearDown() {
        val indexOps = elasticsearchOperations.indexOps(IndexCoordinates.of(TEST_INDEX))
        if (indexOps.exists()) indexOps.delete()
    }

    @Test
    fun `한글 형태소 분석으로 제목의 일부만 검색해도 찾는다`() {
        indexDocument(jobId = 1L, title = "2026 상반기 백엔드 개발자 채용")
        indexDocument(jobId = 2L, title = "프론트엔드 UI 개발자 모집")
        refresh()

        val result = search(query = "백엔드")

        assertThat(result.content.map { it.jobId }).containsExactly(1L)
    }

    @Test
    fun `영문 기술 스택 검색어도 대소문자 구분 없이 찾는다`() {
        indexDocument(jobId = 1L, title = "Kotlin Backend Engineer")
        indexDocument(jobId = 2L, title = "일반 사무직")
        refresh()

        val result = search(query = "kotlin")

        assertThat(result.content.map { it.jobId }).containsExactly(1L)
    }

    @Test
    fun `기업명도 검색 대상에 포함된다`() {
        indexDocument(jobId = 1L, title = "채용 공고", companyName = "인력개발원")
        indexDocument(jobId = 2L, title = "채용 공고", companyName = "다른회사")
        refresh()

        val result = search(query = "인력개발원")

        assertThat(result.content.map { it.jobId }).containsExactly(1L)
    }

    @Test
    fun `공고 본문도 검색 대상에 포함된다`() {
        indexDocument(jobId = 1L, title = "채용", content = "Spring Boot와 Kotlin을 사용합니다")
        indexDocument(jobId = 2L, title = "채용", content = "React를 사용합니다")
        refresh()

        val result = search(query = "Kotlin")

        assertThat(result.content.map { it.jobId }).containsExactly(1L)
    }

    @Test
    fun `근무지역과 고용형태가 색인되어 목록 응답에 그대로 담긴다`() {
        indexDocument(jobId = 1L, title = "채용 공고", location = "서울특별시 중구", employmentType = "인턴")
        refresh()

        val result = search()

        assertThat(result.content).hasSize(1)
        assertThat(result.content[0].location).isEqualTo("서울특별시 중구")
        assertThat(result.content[0].employmentType).isEqualTo("인턴")
    }

    // 두 Field를 Keyword로 색인하고 multiMatch 대상에서 제외한 결정을 고정한다(Issue #169).
    // 검색 대상에 넣으면 기존 검색 결과집합과 순위가 바뀌어 API 계약이 조용히 바뀐다.
    @Test
    fun `근무지역은 검색어 매칭 대상이 아니다`() {
        indexDocument(jobId = 1L, title = "채용 공고", location = "서울특별시 중구")
        refresh()

        val result = search(query = "서울특별시")

        assertThat(result.content).isEmpty()
    }

    @Test
    fun `공고 유형과 기업 유형 필터를 동시에 적용한다`() {
        indexDocument(jobId = 1L, title = "A", postingType = PostingType.MOU, companyType = CompanyType.GENERAL)
        indexDocument(jobId = 2L, title = "B", postingType = PostingType.MOU, companyType = CompanyType.FOREIGN)
        indexDocument(jobId = 3L, title = "C", postingType = PostingType.GENERAL, companyType = CompanyType.GENERAL)
        refresh()

        val result = search(postingType = PostingType.MOU, companyType = CompanyType.GENERAL)

        assertThat(result.content.map { it.jobId }).containsExactly(1L)
    }

    @Test
    fun `출처와 지원 대상 학년 필터를 적용한다`() {
        indexDocument(jobId = 1L, title = "A", sourceName = "MMA", targetGrade = 3)
        indexDocument(jobId = 2L, title = "B", sourceName = "MMA", targetGrade = 2)
        indexDocument(jobId = 3L, title = "C", sourceName = "JOB_ALIO", targetGrade = 3)
        refresh()

        val result = search(sourceName = "MMA", targetGrade = 3)

        assertThat(result.content.map { it.jobId }).containsExactly(1L)
    }

    @Test
    fun `AI 적합성과 난이도 필터를 Elasticsearch에서 정확히 적용한다`() {
        indexDocument(
            jobId = 1L,
            title = "고졸 가능 쉬운 공고",
            highSchoolGraduateFit = AiFitLevel.SUITABLE,
            entryLevelFit = AiFitLevel.CONDITIONAL,
            difficulty = AiDifficulty.EASY,
        )
        indexDocument(
            jobId = 2L,
            title = "신입 가능 어려운 공고",
            highSchoolGraduateFit = AiFitLevel.UNSUITABLE,
            entryLevelFit = AiFitLevel.SUITABLE,
            difficulty = AiDifficulty.HARD,
        )
        indexDocument(jobId = 3L, title = "AI 분석 전 공고")
        refresh()

        assertThat(search().content.map { it.jobId }).containsExactly(3L, 2L, 1L)
        assertThat(search(highSchoolGraduateFit = AiFitLevel.SUITABLE).content.map { it.jobId })
            .containsExactly(1L)
        assertThat(search(entryLevelFit = AiFitLevel.SUITABLE).content.map { it.jobId })
            .containsExactly(2L)
        assertThat(search(difficulty = AiDifficulty.EASY).content.map { it.jobId }).containsExactly(1L)
        assertThat(
            search(
                postingType = PostingType.MOU,
                highSchoolGraduateFit = AiFitLevel.SUITABLE,
                difficulty = AiDifficulty.EASY,
            ).content.map { it.jobId },
        ).containsExactly(1L)
    }

    @Test
    fun `openOnly는 마감 시각이 지난 게시 공고를 제외한다`() {
        val now = LocalDateTime.now()
        indexDocument(jobId = 1L, title = "모집 중", status = JobStatus.PUBLISHED, endDate = now.plusDays(10))
        indexDocument(jobId = 2L, title = "마감 지남", status = JobStatus.PUBLISHED, endDate = now.minusDays(1))
        indexDocument(jobId = 3L, title = "마감 없음", status = JobStatus.PUBLISHED, endDate = null)
        indexDocument(jobId = 4L, title = "마감 상태", status = JobStatus.CLOSED, endDate = now.plusDays(10))
        refresh()

        val result = search(openOnly = true)

        assertThat(result.content.map { it.jobId }).containsExactlyInAnyOrder(1L, 3L)
    }

    @Test
    fun `상태 필터로 게시 또는 마감 공고만 조회한다`() {
        indexDocument(jobId = 1L, title = "A", status = JobStatus.PUBLISHED)
        indexDocument(jobId = 2L, title = "B", status = JobStatus.CLOSED)
        refresh()

        val result = search(status = PublicJobStatus.CLOSED)

        assertThat(result.content.map { it.jobId }).containsExactly(2L)
    }

    @Test
    fun `최신순 정렬은 게시 시각 내림차순이다`() {
        val now = LocalDateTime.now()
        indexDocument(jobId = 1L, title = "A", publishedAt = now.minusDays(2))
        indexDocument(jobId = 2L, title = "B", publishedAt = now.minusDays(1))
        indexDocument(jobId = 3L, title = "C", publishedAt = now)
        refresh()

        val result = search(sort = JobSort.LATEST)

        assertThat(result.content.map { it.jobId }).containsExactly(3L, 2L, 1L)
    }

    @Test
    fun `조회수순 정렬은 조회수 내림차순이다`() {
        indexDocument(jobId = 1L, title = "A", viewCount = 5)
        indexDocument(jobId = 2L, title = "B", viewCount = 100)
        indexDocument(jobId = 3L, title = "C", viewCount = 20)
        refresh()

        val result = search(sort = JobSort.VIEWS)

        assertThat(result.content.map { it.jobId }).containsExactly(2L, 3L, 1L)
    }

    @Test
    fun `direction=ASC를 지정하면 기본 방향이 뒤집힌다`() {
        indexDocument(jobId = 1L, title = "A", viewCount = 5)
        indexDocument(jobId = 2L, title = "B", viewCount = 100)
        indexDocument(jobId = 3L, title = "C", viewCount = 20)
        refresh()

        val result = search(sort = JobSort.VIEWS, direction = SortDirection.ASC)

        assertThat(result.content.map { it.jobId }).containsExactly(1L, 3L, 2L)
    }

    @Test
    fun `direction=DESC를 명시하면 기본값과 동일한 내림차순이다`() {
        val now = LocalDateTime.now()
        indexDocument(jobId = 1L, title = "A", publishedAt = now.minusDays(2))
        indexDocument(jobId = 2L, title = "B", publishedAt = now.minusDays(1))
        refresh()

        val result = search(sort = JobSort.LATEST, direction = SortDirection.DESC)

        assertThat(result.content.map { it.jobId }).containsExactly(2L, 1L)
    }

    @Test
    fun `정렬값이 같아도 공고 ID 내림차순 보조 정렬로 안정적이다`() {
        val now = LocalDateTime.now()
        (1..5).forEach { indexDocument(jobId = it.toLong(), title = "공고 $it", publishedAt = now) }
        refresh()

        val result = search(sort = JobSort.LATEST)

        assertThat(result.content.map { it.jobId }).containsExactly(5L, 4L, 3L, 2L, 1L)
    }

    @Test
    fun `검색 결과가 없으면 빈 목록을 반환한다`() {
        val result = search(query = "존재하지-않는-검색어")

        assertThat(result.content).isEmpty()
        assertThat(result.totalElements).isEqualTo(0)
        assertThat(result.totalPages).isEqualTo(0)
    }

    @Test
    fun `Pagination은 Elasticsearch 단계에서 처리되고 안정적이다`() {
        val now = LocalDateTime.now()
        (1..25).forEach {
            indexDocument(
                jobId = it.toLong(),
                title = "공고 $it",
                publishedAt = now.plusSeconds(it.toLong()),
            )
        }
        refresh()

        val firstPage = search(sort = JobSort.LATEST, pageable = PageRequest.of(0, 10))
        val secondPage = search(sort = JobSort.LATEST, pageable = PageRequest.of(1, 10))

        assertThat(firstPage.totalElements).isEqualTo(25)
        assertThat(firstPage.totalPages).isEqualTo(3)
        assertThat(firstPage.content).hasSize(10)
        assertThat(secondPage.content).hasSize(10)
        val allIds = (firstPage.content + secondPage.content).map { it.jobId }
        assertThat(allIds).doesNotHaveDuplicates()
    }

    @Test
    fun `색인을 갱신하면 최신 값으로 덮어써진다`() {
        indexDocument(jobId = 1L, title = "원래 제목")
        refresh()
        assertThat(search(query = "원래").content).hasSize(1)

        indexDocument(jobId = 1L, title = "수정된 제목")
        refresh()

        assertThat(search(query = "원래").content).isEmpty()
        assertThat(search(query = "수정된").content).hasSize(1)
    }

    @Test
    fun `색인을 삭제하면 검색 결과에서 사라진다`() {
        indexDocument(jobId = 1L, title = "삭제될 공고")
        refresh()
        assertThat(search(query = "삭제될").content).hasSize(1)

        indexManager.delete(1L)
        refresh()

        assertThat(search(query = "삭제될").content).isEmpty()
    }

    @Test
    fun `존재하지 않는 Document를 삭제해도 예외 없이 멱등하게 처리된다`() {
        indexManager.delete(999L)
    }

    @Test
    fun `기업이 삭제된 공고는 company가 null인 채로 검색 결과에 나온다`() {
        indexDocument(jobId = 1L, title = "기업 삭제된 공고", companyName = null)
        refresh()

        val result = search(query = "기업 삭제된")

        assertThat(result.content).hasSize(1)
        assertThat(result.content.single().company).isNull()
    }

    @Test
    fun `AI 분석 결과 필드가 색인에 그대로 왕복된다`() {
        // List<Long>/Keyword Field가 실제 Elasticsearch Mapping에서 깨지지 않는지 확인한다
        // (Issue #144 -- JobSearchDocument.publishedAt의 Date Format처럼 실제 Elasticsearch로
        // 확인하지 않으면 Mapping 문제를 놓칠 수 있다). JobSummaryResponse는 AI 필드를 그대로
        // 노출하지 않으므로(Issue #144 제외 범위 -- 새 API Field 확장은 하지 않음), Elasticsearch
        // Document를 직접 다시 읽어 검증한다.
        indexManager.upsert(
            documentOf(jobId = 1L, title = "AI 분석 완료 공고").copy(
                requiredTechStackIds = listOf(10L, 11L),
                preferredTechStackIds = listOf(20L),
                highSchoolGraduateFit = "SUITABLE",
                entryLevelFit = "CONDITIONAL",
                difficulty = "HARD",
            ),
        )
        refresh()

        val stored =
            elasticsearchOperations.get("1", JobSearchDocument::class.java, IndexCoordinates.of(TEST_INDEX))

        assertThat(stored).isNotNull
        assertThat(stored!!.requiredTechStackIds).containsExactlyInAnyOrder(10L, 11L)
        assertThat(stored.preferredTechStackIds).containsExactly(20L)
        assertThat(stored.highSchoolGraduateFit).isEqualTo("SUITABLE")
        assertThat(stored.entryLevelFit).isEqualTo("CONDITIONAL")
        assertThat(stored.difficulty).isEqualTo("HARD")
    }

    @Test
    fun `AI 분석이 아직 없는 공고는 AI 필드가 빈 값으로 색인된다`() {
        indexDocument(jobId = 1L, title = "AI 분석 전 공고")
        refresh()

        val stored =
            elasticsearchOperations.get("1", JobSearchDocument::class.java, IndexCoordinates.of(TEST_INDEX))

        assertThat(stored).isNotNull
        assertThat(stored!!.requiredTechStackIds).isEmpty()
        assertThat(stored.preferredTechStackIds).isEmpty()
        assertThat(stored.highSchoolGraduateFit).isNull()
        assertThat(stored.entryLevelFit).isNull()
        assertThat(stored.difficulty).isNull()
    }

    @Test
    fun `Bulk 색인은 여러 건을 한 번에 반영한다`() {
        val documents = (1..5).map { documentOf(jobId = it.toLong(), title = "공고 $it") }
        indexManager.bulkIndex(TEST_INDEX, documents)
        refresh()

        val result = search()

        assertThat(result.totalElements).isEqualTo(5)
    }

    private fun refresh() {
        elasticsearchOperations.indexOps(IndexCoordinates.of(TEST_INDEX)).refresh()
    }

    @Suppress("LongParameterList")
    private fun search(
        query: String? = null,
        postingType: PostingType? = null,
        status: PublicJobStatus? = null,
        companyType: CompanyType? = null,
        sourceName: String? = null,
        targetGrade: Int? = null,
        highSchoolGraduateFit: AiFitLevel? = null,
        entryLevelFit: AiFitLevel? = null,
        difficulty: AiDifficulty? = null,
        openOnly: Boolean = false,
        sort: JobSort = JobSort.LATEST,
        direction: SortDirection? = null,
        pageable: PageRequest = PageRequest.of(0, 20),
    ) = searchService.search(
        query,
        postingType,
        status,
        companyType,
        sourceName,
        targetGrade,
        highSchoolGraduateFit,
        entryLevelFit,
        difficulty,
        openOnly,
        sort,
        direction,
        pageable,
        REQUESTER_ID,
    )

    @Suppress("LongParameterList")
    private fun indexDocument(
        jobId: Long,
        title: String,
        content: String? = null,
        postingType: PostingType = PostingType.MOU,
        companyName: String? = "인력개발원",
        companyType: CompanyType = CompanyType.GENERAL,
        sourceName: String? = null,
        targetGrade: Int? = null,
        highSchoolGraduateFit: AiFitLevel? = null,
        entryLevelFit: AiFitLevel? = null,
        difficulty: AiDifficulty? = null,
        status: JobStatus = JobStatus.PUBLISHED,
        viewCount: Long = 0,
        publishedAt: LocalDateTime? = LocalDateTime.now(),
        endDate: LocalDateTime? = null,
        location: String? = null,
        employmentType: String? = null,
    ) {
        indexManager.upsert(
            documentOf(
                jobId = jobId,
                title = title,
                content = content,
                postingType = postingType,
                companyName = companyName,
                companyType = companyType,
                sourceName = sourceName,
                targetGrade = targetGrade,
                highSchoolGraduateFit = highSchoolGraduateFit,
                entryLevelFit = entryLevelFit,
                difficulty = difficulty,
                status = status,
                viewCount = viewCount,
                publishedAt = publishedAt,
                endDate = endDate,
                location = location,
                employmentType = employmentType,
            ),
        )
    }

    @Suppress("LongParameterList")
    private fun documentOf(
        jobId: Long,
        title: String,
        content: String? = null,
        postingType: PostingType = PostingType.MOU,
        companyName: String? = "인력개발원",
        companyType: CompanyType = CompanyType.GENERAL,
        sourceName: String? = null,
        targetGrade: Int? = null,
        highSchoolGraduateFit: AiFitLevel? = null,
        entryLevelFit: AiFitLevel? = null,
        difficulty: AiDifficulty? = null,
        status: JobStatus = JobStatus.PUBLISHED,
        viewCount: Long = 0,
        publishedAt: LocalDateTime? = LocalDateTime.now(),
        endDate: LocalDateTime? = null,
        location: String? = null,
        employmentType: String? = null,
    ) = JobSearchDocument(
        id = jobId.toString(),
        jobId = jobId,
        title = title,
        content = content,
        postingType = postingType.name,
        applicationMethod = ApplicationMethod.EXTERNAL.name,
        status = status.name,
        companyId = 1L,
        companyName = companyName,
        companyType = companyType.name,
        companyLogoFileId = null,
        sourceName = sourceName,
        targetGrade = targetGrade,
        highSchoolGraduateFit = highSchoolGraduateFit?.name,
        entryLevelFit = entryLevelFit?.name,
        difficulty = difficulty?.name,
        capacity = null,
        firstComeServed = false,
        viewCount = viewCount,
        publishedAt = publishedAt,
        startDate = null,
        endDate = endDate,
        location = location,
        employmentType = employmentType,
    )

    /** 항상 빈 Map을 돌려주는 최소 구현이다. 이 Test는 Query 정확성만 다루고 File URL 발급은 다루지 않는다. */
    private object NoOpFileUrlPort : FileUrlPort {
        override fun presignedImageUrls(
            requesterId: Long,
            fileIds: Collection<Long>,
        ): Map<Long, String> = emptyMap()
    }

    private object NoOpJobApplicationEligibilityAccessor : JobApplicationEligibilityAccessor {
        override fun findAllByJobIds(
            jobIds: Set<Long>,
            requesterMemberId: Long,
        ): Map<Long, JobApplicationEligibilityAccessSnapshot> =
            jobIds.associateWith {
                JobApplicationEligibilityAccessSnapshot(
                    canApply = false,
                    eligibilityReason = "NOT_ENROLLED",
                    eligibilityMessage = "",
                    applicationId = null,
                    applicationStatus = null,
                    availableActions = emptyList(),
                )
            }
    }

    /** 항상 빈 값을 돌려주는 최소 구현이다. 이 Test는 Query 정확성만 다루고 북마크 여부·수는 다루지 않는다. */
    private object NoOpJobBookmarkAccessor : JobBookmarkAccessor {
        override fun findAllByJobIds(
            jobIds: Set<Long>,
            requesterMemberId: Long,
        ): Set<Long> = emptySet()

        override fun countAllByJobIds(jobIds: Set<Long>): Map<Long, Long> = emptyMap()
    }

    companion object {
        private const val TEST_INDEX = "jobs-search-integration-test"
        private const val REQUESTER_ID = 1L

        @Container
        @JvmStatic
        val elasticsearch: GenericContainer<*> =
            GenericContainer(ImageFromDockerfile().withFileFromPath(".", Paths.get("docker/elasticsearch")))
                .withEnv("discovery.type", "single-node")
                .withEnv("xpack.security.enabled", "false")
                .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m")
                .withExposedPorts(9200)
                .waitingFor(Wait.forHttp("/_cluster/health").forStatusCode(200).forPort(9200))

        @JvmStatic
        @BeforeAll
        fun beforeAll() {
            elasticsearch.start()
        }

        @JvmStatic
        @DynamicPropertySource
        fun elasticsearchProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.elasticsearch.uris") {
                "http://${elasticsearch.host}:${elasticsearch.getMappedPort(9200)}"
            }
        }
    }
}
