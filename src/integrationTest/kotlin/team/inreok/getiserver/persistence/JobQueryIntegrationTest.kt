package team.inreok.getiserver.persistence

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import team.inreok.getiserver.domain.company.entity.Company
import team.inreok.getiserver.domain.company.entity.type.CompanyType
import team.inreok.getiserver.domain.company.repository.CompanyRepository
import team.inreok.getiserver.domain.job.dto.JobSort
import team.inreok.getiserver.domain.job.dto.PublicJobStatus
import team.inreok.getiserver.domain.job.entity.Job
import team.inreok.getiserver.domain.job.entity.type.ApplicationMethod
import team.inreok.getiserver.domain.job.entity.type.JobStatus
import team.inreok.getiserver.domain.job.entity.type.PostingType
import team.inreok.getiserver.domain.job.repository.JobRepository
import java.time.LocalDateTime
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Job 조회 Query가 실제 PostgreSQL에서 의도대로 동작하는지 검증한다.
 * Service/Controller Test는 Mock Repository를 사용하므로 JPQL의 `LIKE ... ESCAPE`,
 * Soft Delete 제외, `NULLS LAST` 정렬, 조회수 원자 증가, `jobs.company_id` FK는 이 Test에서만
 * 확인된다.
 */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration::class)
class JobQueryIntegrationTest
    @Autowired
    constructor(
        private val jobRepository: JobRepository,
        private val companyRepository: CompanyRepository,
    ) {
        private val now: LocalDateTime = LocalDateTime.of(2026, 8, 2, 12, 0)
        private var companyId: Long = 0

        @BeforeEach
        fun setUp() {
            jobRepository.deleteAll()
            jobRepository.flush()
            companyRepository.deleteAll()
            companyRepository.flush()
            companyId =
                companyRepository
                    .saveAndFlush(Company(name = "인력개발원", type = CompanyType.GENERAL))
                    .id!!
        }

        @Test
        fun `Soft Delete된 공고는 공개 목록에서 제외된다`() {
            persist(title = "살아있는 공고")
            persist(title = "삭제된 공고", deletedAt = now.minusDays(1))

            val result = search()

            assertThat(result.content.map { it.title }).containsExactly("살아있는 공고")
        }

        @Test
        fun `임시저장과 삭제 상태 공고는 공개 목록에서 제외된다`() {
            persist(title = "게시 공고", status = JobStatus.PUBLISHED)
            persist(title = "마감 공고", status = JobStatus.CLOSED)
            persist(title = "임시저장 공고", status = JobStatus.DRAFT)
            persist(title = "삭제 상태 공고", status = JobStatus.DELETED)

            val result = search()

            assertThat(result.content.map { it.title }).containsExactlyInAnyOrder("게시 공고", "마감 공고")
        }

        @Test
        fun `검색어의 LIKE Wildcard를 문자 그대로 매칭한다`() {
            persist(title = "100%_할인 공고")
            persist(title = "1000원 할인 공고")

            // Service가 만드는 Pattern 형태를 그대로 넘긴다(escapeLikePattern은 Module 내부라 참조 불가).
            val escaped = search(titlePattern = """%100\%\_할인%""")
            assertThat(escaped.content.map { it.title }).containsExactly("100%_할인 공고")

            // Escape하지 않으면 %와 _가 Wildcard로 해석되어 두 건 모두 매칭된다.
            val notEscaped = search(titlePattern = "%100%_할인%")
            assertThat(notEscaped.content).hasSize(2)
        }

        @Test
        fun `제목 검색은 대소문자를 무시한다`() {
            persist(title = "Backend Engineer")

            // Service는 Pattern을 소문자로 만들어 넘기고 Query가 LOWER(title)과 비교한다.
            assertThat(search(titlePattern = "%backend%").content).hasSize(1)
        }

        @Test
        fun `검색어가 없으면 제목 조건 없이 전체를 조회한다`() {
            persist(title = "공고 A")
            persist(title = "공고 B")

            // titlePattern이 null일 때 Parameter 타입 추론이 깨지지 않는지 확인한다.
            assertThat(search().content).hasSize(2)
        }

        @Test
        fun `openOnly는 마감 시각이 지난 공고를 제외한다`() {
            persist(title = "모집 중", endDate = now.plusDays(10))
            persist(title = "마감 지남", endDate = now.minusDays(1))
            persist(title = "마감 없음", endDate = null)

            val result = search(openOnly = true)

            assertThat(result.content.map { it.title }).containsExactlyInAnyOrder("모집 중", "마감 없음")
        }

        @Test
        fun `openOnly는 마감 상태 공고를 제외한다`() {
            persist(title = "마감 상태", status = JobStatus.CLOSED, endDate = now.plusDays(10))

            assertThat(search(openOnly = true).content).isEmpty()
        }

        @Test
        fun `공고 유형으로 필터링한다`() {
            persist(title = "MOU 공고", postingType = PostingType.MOU)
            persist(title = "일반 공고", postingType = PostingType.GENERAL)

            val result = search(postingType = PostingType.MOU)

            assertThat(result.content.map { it.title }).containsExactly("MOU 공고")
        }

        @Test
        fun `최신순 정렬은 게시 시각이 없는 공고를 뒤로 보낸다`() {
            persist(title = "게시 시각 없음", publishedAt = null)
            persist(title = "먼저 게시", publishedAt = now.minusDays(2))
            persist(title = "나중 게시", publishedAt = now.minusDays(1))

            val result = search()

            assertThat(result.content.map { it.title })
                .containsExactly("나중 게시", "먼저 게시", "게시 시각 없음")
        }

        @Test
        fun `마감 임박순 정렬은 마감 시각이 없는 공고를 뒤로 보낸다`() {
            persist(title = "마감 없음", endDate = null)
            persist(title = "늦게 마감", endDate = now.plusDays(10))
            persist(title = "빨리 마감", endDate = now.plusDays(1))

            val result = search(sort = JobSort.DEADLINE)

            assertThat(result.content.map { it.title })
                .containsExactly("빨리 마감", "늦게 마감", "마감 없음")
        }

        @Test
        fun `정렬값이 같아도 공고 ID 보조 정렬로 Page 사이 중복과 누락이 없다`() {
            // 게시 시각이 모두 같으면 보조 정렬이 없을 때 Page마다 순서가 흔들릴 수 있다.
            repeat(10) { persist(title = "공고 $it", publishedAt = now) }

            val first =
                jobRepository.searchPublic(
                    PublicJobStatus.ALL_VISIBLE,
                    null,
                    null,
                    false,
                    now,
                    PageRequest.of(0, 4, JobSort.LATEST.toSort()),
                )
            val second =
                jobRepository.searchPublic(
                    PublicJobStatus.ALL_VISIBLE,
                    null,
                    null,
                    false,
                    now,
                    PageRequest.of(1, 4, JobSort.LATEST.toSort()),
                )
            val third =
                jobRepository.searchPublic(
                    PublicJobStatus.ALL_VISIBLE,
                    null,
                    null,
                    false,
                    now,
                    PageRequest.of(2, 4, JobSort.LATEST.toSort()),
                )

            val ids = (first.content + second.content + third.content).map { it.id }
            assertThat(ids).hasSize(10)
            assertThat(ids).doesNotHaveDuplicates()
            // id DESC이므로 전체를 이어붙이면 내림차순이어야 한다.
            assertThat(ids).isSortedAccordingTo(compareByDescending { it })
        }

        /**
         * `@DataJpaTest`는 Test를 Transaction으로 감싸고 끝에 Rollback한다. 그 안에서 저장한
         * 공고는 Commit되지 않아 다른 Thread의 Connection에서 보이지 않고, UPDATE가 0건을
         * 갱신해 이 Test의 의미가 사라진다. 실제 동시 요청을 재현하려면 Test 자체가 Transaction
         * 밖에서 돌아야 한다.
         */
        @Test
        @Transactional(propagation = Propagation.NOT_SUPPORTED)
        fun `조회수는 동시 요청에서도 증가분이 유실되지 않는다`() {
            val jobId = persist(title = "인기 공고").id!!
            val threads = 8
            val perThread = 25

            val executor = Executors.newFixedThreadPool(threads)
            repeat(threads) {
                executor.submit {
                    repeat(perThread) { jobRepository.incrementViewCount(jobId) }
                }
            }
            executor.shutdown()
            assertThat(executor.awaitTermination(60, TimeUnit.SECONDS)).isTrue()

            assertThat(jobRepository.findById(jobId).orElseThrow().viewCount)
                .isEqualTo((threads * perThread).toLong())
        }

        @Test
        fun `존재하지 않는 기업으로 공고를 저장하면 FK 제약에 걸린다`() {
            val job =
                Job(
                    companyId = 999_999L,
                    type = PostingType.MOU,
                    applicationMethod = ApplicationMethod.EXTERNAL,
                    title = "없는 기업 공고",
                    status = JobStatus.PUBLISHED,
                )

            assertThatThrownBy { jobRepository.saveAndFlush(job) }
                .isInstanceOf(DataIntegrityViolationException::class.java)
        }

        @Test
        fun `Soft Delete된 공고는 상세 조회에서도 제외되지만 행은 남는다`() {
            val jobId = persist(title = "삭제된 공고", deletedAt = now).id!!

            assertThat(jobRepository.findByIdAndDeletedAtIsNull(jobId)).isNull()
            // 이력 보존: 실제 행은 그대로 있어야 북마크와 지원 이력이 살아남는다.
            assertThat(jobRepository.findById(jobId)).isPresent()
        }

        private fun search(
            titlePattern: String? = null,
            postingType: PostingType? = null,
            openOnly: Boolean = false,
            sort: JobSort = JobSort.LATEST,
        ) = jobRepository.searchPublic(
            PublicJobStatus.ALL_VISIBLE,
            titlePattern,
            postingType,
            openOnly,
            now,
            PageRequest.of(0, 20, sort.toSort()),
        )

        private fun persist(
            title: String,
            status: JobStatus = JobStatus.PUBLISHED,
            postingType: PostingType = PostingType.MOU,
            endDate: LocalDateTime? = null,
            publishedAt: LocalDateTime? = LocalDateTime.of(2026, 7, 1, 0, 0),
            deletedAt: LocalDateTime? = null,
        ): Job =
            jobRepository.saveAndFlush(
                Job(
                    companyId = companyId,
                    type = postingType,
                    applicationMethod = ApplicationMethod.EXTERNAL,
                    title = title,
                    status = status,
                ).apply {
                    bodyMarkdown = "## 모집 부문"
                    externalUrl = "https://example.com/apply"
                    recruitmentEndedAt = endDate
                    this.publishedAt = publishedAt
                    this.deletedAt = deletedAt
                },
            )

        // Sort는 JobSort가 만들어 주지만, 이 Test가 기대하는 보조 정렬이 유지되는지 명시적으로 확인한다.
        @Test
        fun `JobSort는 항상 공고 ID 내림차순 보조 정렬을 포함한다`() {
            JobSort.entries.forEach { sort ->
                val orders = sort.toSort().toList()
                assertThat(orders.last().property).isEqualTo("id")
                assertThat(orders.last().direction).isEqualTo(Sort.Direction.DESC)
            }
        }

        companion object {
            @Container
            @ServiceConnection
            @JvmStatic
            val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:18.4-alpine"))
        }
    }
