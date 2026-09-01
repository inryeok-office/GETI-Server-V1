package team.inreok.getiserver.domain.job

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
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

@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration::class)
class CompanyJobQueryIntegrationTest
    @Autowired
    constructor(
        private val companyRepository: CompanyRepository,
        private val jobRepository: JobRepository,
    ) {
        @BeforeEach
        fun setUp() {
            jobRepository.deleteAll()
            companyRepository.deleteAll()
        }

        @Test
        fun `기업 ID 집합의 삭제되지 않은 공고를 한 번에 조회한다`() {
            val firstCompanyId = persistCompany("첫 기업")
            val secondCompanyId = persistCompany("둘째 기업")
            persistJob(firstCompanyId, "공개", JobStatus.PUBLISHED)
            persistJob(secondCompanyId, "마감", JobStatus.CLOSED)
            persistJob(firstCompanyId, "삭제", JobStatus.PUBLISHED, deleted = true)

            val result =
                jobRepository.findAllByCompanyIdInAndStatusInAndDeletedAtIsNull(
                    setOf(firstCompanyId, secondCompanyId),
                    listOf(JobStatus.DRAFT, JobStatus.PUBLISHED, JobStatus.CLOSED),
                )

            assertThat(result.map { it.title }).containsExactlyInAnyOrder("공개", "마감")
        }

        @Test
        fun `활성 공고 존재 여부는 공개 상태와 마감 시각을 함께 적용한다`() {
            val activeCompanyId = persistCompany("활성 기업")
            val expiredCompanyId = persistCompany("마감 기업")
            val closedCompanyId = persistCompany("종료 기업")
            val deletedCompanyId = persistCompany("삭제 공고 기업")
            persistJob(activeCompanyId, "상시 모집", JobStatus.PUBLISHED)
            persistJob(
                expiredCompanyId,
                "기한 마감",
                JobStatus.PUBLISHED,
                endedAt = LocalDateTime.now().minusYears(1),
            )
            persistJob(closedCompanyId, "종료", JobStatus.CLOSED)
            persistJob(deletedCompanyId, "삭제", JobStatus.PUBLISHED, deleted = true)
            val now = LocalDateTime.now()

            assertThat(jobRepository.existsActiveByCompanyId(activeCompanyId, now)).isTrue()
            assertThat(jobRepository.existsActiveByCompanyId(expiredCompanyId, now)).isFalse()
            assertThat(jobRepository.existsActiveByCompanyId(closedCompanyId, now)).isFalse()
            assertThat(jobRepository.existsActiveByCompanyId(deletedCompanyId, now)).isFalse()
        }

        private fun persistCompany(name: String): Long =
            requireNotNull(companyRepository.saveAndFlush(Company(name, CompanyType.GENERAL)).id)

        private fun persistJob(
            companyId: Long,
            title: String,
            status: JobStatus,
            endedAt: LocalDateTime? = null,
            deleted: Boolean = false,
        ) {
            jobRepository.saveAndFlush(
                Job(
                    companyId = companyId,
                    type = PostingType.GENERAL,
                    applicationMethod = ApplicationMethod.INTERNAL,
                    title = title,
                    status = status,
                ).apply {
                    recruitmentEndedAt = endedAt
                    if (deleted) deletedAt = LocalDateTime.now()
                },
            )
        }

        companion object {
            @Container
            @ServiceConnection
            @JvmStatic
            val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:18.4-alpine"))
        }
    }
