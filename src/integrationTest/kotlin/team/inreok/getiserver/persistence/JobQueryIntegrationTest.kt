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
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import team.inreok.getiserver.domain.company.entity.Company
import team.inreok.getiserver.domain.company.entity.type.CompanyType
import team.inreok.getiserver.domain.company.repository.CompanyRepository
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
 *
 * `GET /api/v1/jobs`의 검색·필터·정렬은 Issue #69에서 Elasticsearch로 옮겨졌고(옛
 * `JobRepository.searchPublic`의 LIKE/필터/정렬 Test는 `domain.search`의 Testcontainers
 * Elasticsearch Integration Test로 이동했다), 이 Test는 여전히 Postgres에 남아 있는 조회
 * (`findByIdAndDeletedAtIsNull`, `incrementViewCount`, `findForReindex`, FK 제약)만 다룬다.
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
        fun `Soft Delete된 공고는 상세 조회에서도 제외되지만 행은 남는다`() {
            val jobId = persist(title = "삭제된 공고", deletedAt = now).id!!

            assertThat(jobRepository.findByIdAndDeletedAtIsNull(jobId)).isNull()
            // 이력 보존: 실제 행은 그대로 있어야 북마크와 지원 이력이 살아남는다.
            assertThat(jobRepository.findById(jobId)).isPresent()
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

        // --- findForReindex(Issue #69, 전체 재색인용 Keyset Pagination 조회) ---

        @Test
        fun `재색인 조회는 삭제와 임시저장 공고를 제외한다`() {
            persist(title = "게시 공고", status = JobStatus.PUBLISHED)
            persist(title = "마감 공고", status = JobStatus.CLOSED)
            persist(title = "임시저장 공고", status = JobStatus.DRAFT)
            persist(title = "삭제 상태 공고", status = JobStatus.DELETED)
            persist(title = "삭제된 공고", deletedAt = now)

            val result =
                jobRepository.findForReindex(
                    listOf(JobStatus.PUBLISHED, JobStatus.CLOSED),
                    0L,
                    PageRequest.of(0, 20),
                )

            assertThat(result.content.map { it.title }).containsExactlyInAnyOrder("게시 공고", "마감 공고")
        }

        @Test
        fun `재색인 조회는 id 오름차순 Keyset Pagination으로 중복과 누락 없이 전체를 순회한다`() {
            val ids = (1..10).map { persist(title = "공고 $it").id!! }.sorted()

            val collected = mutableListOf<Long>()
            var afterId = 0L
            while (true) {
                val page =
                    jobRepository.findForReindex(
                        listOf(JobStatus.PUBLISHED, JobStatus.CLOSED),
                        afterId,
                        PageRequest.of(0, 4),
                    )
                if (page.content.isEmpty()) break
                collected += page.content.map { it.id!! }
                afterId = page.content.last().id!!
            }

            assertThat(collected).containsExactlyElementsOf(ids)
        }

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

        companion object {
            @Container
            @ServiceConnection
            @JvmStatic
            val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:18.4-alpine"))
        }
    }
