package team.inreok.getiserver.persistence

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
import team.inreok.getiserver.domain.collector.entity.JobNotificationDelivery
import team.inreok.getiserver.domain.collector.entity.type.JobNotificationDeliveryStatus
import team.inreok.getiserver.domain.collector.repository.JobNotificationDeliveryRepository
import team.inreok.getiserver.domain.company.entity.Company
import team.inreok.getiserver.domain.company.entity.type.CompanyType
import team.inreok.getiserver.domain.company.repository.CompanyRepository
import team.inreok.getiserver.domain.job.entity.Job
import team.inreok.getiserver.domain.job.entity.type.ApplicationMethod
import team.inreok.getiserver.domain.job.entity.type.JobStatus
import team.inreok.getiserver.domain.job.entity.type.PostingType
import team.inreok.getiserver.domain.job.repository.JobRepository
import java.time.LocalDateTime

/**
 * PR #68 Code Review Finding #1(High)을 회귀 방지하기 위한 Test다: `markFailedFinal`이 만드는
 * 최종 실패(non-retryable) Row(`status=FAILED`, `nextRetryAt=null`)가 `findDueForRetry`에
 * 다시 걸려 정책과 다르게 재시도되지 않는지 실제 PostgreSQL로 검증한다. Mock Repository로는
 * JPQL 쿼리 시맨틱(PENDING과 FAILED-with-null-nextRetryAt을 구분하는지)을 검증할 수 없다.
 */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration::class)
class JobNotificationDeliveryRepositoryIntegrationTest
    @Autowired
    constructor(
        private val deliveryRepository: JobNotificationDeliveryRepository,
        private val jobRepository: JobRepository,
        private val companyRepository: CompanyRepository,
    ) {
        private val now: LocalDateTime = LocalDateTime.of(2026, 8, 4, 12, 0)
        private var companyId: Long = 0

        @BeforeEach
        fun setUp() {
            deliveryRepository.deleteAll()
            deliveryRepository.flush()
            jobRepository.deleteAll()
            jobRepository.flush()
            companyRepository.deleteAll()
            companyRepository.flush()

            companyId = companyRepository.saveAndFlush(Company(name = "인력개발원", type = CompanyType.GENERAL)).id!!
        }

        // job_id는 Unique Constraint 대상이라, 한 Test 안에서 Delivery를 여러 건 만들 때마다
        // 매번 새 Job을 만들어 서로 다른 jobId를 쓴다("job_id는 Unique Constraint로..." Test만
        // 예외적으로 jobId를 명시로 재사용해 제약을 직접 검증한다).
        private fun newJobId(): Long =
            jobRepository
                .saveAndFlush(
                    Job(
                        companyId = companyId,
                        type = PostingType.GENERAL,
                        applicationMethod = ApplicationMethod.EXTERNAL,
                        title = "백엔드 개발자",
                        status = JobStatus.PUBLISHED,
                    ),
                ).id!!

        private fun deliveryOf(
            status: JobNotificationDeliveryStatus,
            nextRetryAt: LocalDateTime?,
            jobId: Long = newJobId(),
        ): JobNotificationDelivery =
            deliveryRepository.saveAndFlush(
                JobNotificationDelivery(
                    jobId = jobId,
                    sourceCode = "MMA",
                    sourceDisplayName = "병역일터",
                    title = "백엔드 개발자",
                    companyName = "인력개발원",
                ).apply {
                    this.status = status
                    this.nextRetryAt = nextRetryAt
                },
            )

        @Test
        fun `아직 시도하지 않은 PENDING Row는 nextRetryAt이 없어도 재시도 대상이다`() {
            val pending = deliveryOf(JobNotificationDeliveryStatus.PENDING, nextRetryAt = null)

            val due = deliveryRepository.findDueForRetry(now)

            assertThat(due.map { it.id }).containsExactly(pending.id)
        }

        @Test
        fun `재시도 가능한 실패는 nextRetryAt이 지났을 때만 대상이다`() {
            val due1 = deliveryOf(JobNotificationDeliveryStatus.FAILED, nextRetryAt = now.minusMinutes(1))
            val notYetDue = deliveryOf(JobNotificationDeliveryStatus.FAILED, nextRetryAt = now.plusMinutes(1))

            val due = deliveryRepository.findDueForRetry(now)

            assertThat(due.map { it.id }).containsExactly(due1.id)
            assertThat(due.map { it.id }).doesNotContain(notYetDue.id)
        }

        @Test
        fun `markFailedFinal이 만드는 nextRetryAt이 없는 FAILED Row는 재시도 대상에서 제외한다`() {
            // markFailedFinal(비재시도 4xx 등)은 status=FAILED, nextRetryAt=null로 저장한다.
            // 이 Row가 PENDING과 구분되지 않고 다시 조회되면 Code Review Finding #1의 결함이 재발한다.
            val permanentlyFailed = deliveryOf(JobNotificationDeliveryStatus.FAILED, nextRetryAt = null)

            val due = deliveryRepository.findDueForRetry(now)

            assertThat(due.map { it.id }).doesNotContain(permanentlyFailed.id)
        }

        @Test
        fun `SENT 상태는 재시도 대상이 아니다`() {
            val sent = deliveryOf(JobNotificationDeliveryStatus.SENT, nextRetryAt = null)

            val due = deliveryRepository.findDueForRetry(now)

            assertThat(due.map { it.id }).doesNotContain(sent.id)
        }

        @Test
        fun `findAllByStatus는 SENDING Row만 반환한다(서버 재시작 복구용)`() {
            val sending = deliveryOf(JobNotificationDeliveryStatus.SENDING, nextRetryAt = null)
            deliveryOf(JobNotificationDeliveryStatus.PENDING, nextRetryAt = null)

            val stale = deliveryRepository.findAllByStatus(JobNotificationDeliveryStatus.SENDING)

            assertThat(stale.map { it.id }).containsExactly(sending.id)
        }

        @Test
        fun `job_id는 Unique Constraint로 중복 Delivery 생성을 막는다`() {
            val sharedJobId = newJobId()
            deliveryOf(JobNotificationDeliveryStatus.PENDING, nextRetryAt = null, jobId = sharedJobId)

            assertThat(
                runCatching {
                    deliveryOf(JobNotificationDeliveryStatus.PENDING, nextRetryAt = null, jobId = sharedJobId)
                }.isFailure,
            ).isTrue()
        }

        companion object {
            @Container
            @ServiceConnection
            @JvmStatic
            val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:18.4-alpine"))
        }
    }
