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
import org.springframework.data.domain.PageRequest
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import team.inreok.getiserver.domain.collector.entity.CollectionRun
import team.inreok.getiserver.domain.collector.entity.CollectionRunError
import team.inreok.getiserver.domain.collector.entity.type.CollectionRunStatus
import team.inreok.getiserver.domain.collector.entity.type.CollectorAction
import team.inreok.getiserver.domain.collector.entity.type.JobSourceApprovalStatus
import team.inreok.getiserver.domain.collector.entity.type.JobSourceCode
import team.inreok.getiserver.domain.collector.entity.type.JobSourceType
import team.inreok.getiserver.domain.collector.repository.CollectionRunErrorRepository
import team.inreok.getiserver.domain.collector.repository.CollectionRunRepository
import team.inreok.getiserver.domain.collector.repository.JobSourceRepository
import java.time.LocalDateTime
import team.inreok.getiserver.domain.collector.entity.JobSource as JobSourceEntity

/**
 * Collector Repository가 실제 PostgreSQL에서 의도대로 동작하는지 검증한다. Service/Controller
 * Test는 Mock Repository를 사용하므로 V8 Seed 데이터, 복합 필터, Pagination, FK 관계는 이
 * Test에서만 확인된다.
 */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration::class)
class CollectorRepositoryIntegrationTest
    @Autowired
    constructor(
        private val jobSourceRepository: JobSourceRepository,
        private val collectionRunRepository: CollectionRunRepository,
        private val collectionRunErrorRepository: CollectionRunErrorRepository,
    ) {
        private val now: LocalDateTime = LocalDateTime.of(2026, 8, 3, 12, 0)

        @BeforeEach
        fun setUp() {
            collectionRunErrorRepository.deleteAll()
            collectionRunErrorRepository.flush()
            collectionRunRepository.deleteAll()
            collectionRunRepository.flush()
        }

        @Test
        fun `V8 Migration이 확정된 7개 외부 수집원을 실제 승인 현황과 비활성 상태로 시드한다`() {
            val sources = jobSourceRepository.findAllByOrderBySourceCodeAsc()

            // sourceCode는 STRING Enum이라 정렬은 선언 순서가 아니라 알파벳순이다.
            assertThat(sources.map { it.sourceCode })
                .containsExactlyInAnyOrder(
                    JobSourceCode.MMA,
                    JobSourceCode.JOB_ALIO,
                    JobSourceCode.CLEAN_EYE,
                    JobSourceCode.NARA_ILTEO,
                    JobSourceCode.IBK_ONE_JOB,
                    JobSourceCode.SARAMIN,
                    JobSourceCode.WORK24,
                )
            val names = sources.map { it.sourceCode.name }
            assertThat(names).isEqualTo(names.sorted())
            assertThat(sources).allSatisfy { source ->
                assertThat(source.enabled).isFalse()
                assertThat(source.sourceType).isEqualTo(JobSourceType.EXTERNAL_API)
            }
            val approvalStatusByCode = sources.associate { it.sourceCode to it.approvalStatus }
            assertThat(approvalStatusByCode[JobSourceCode.MMA]).isEqualTo(JobSourceApprovalStatus.READY)
            assertThat(approvalStatusByCode[JobSourceCode.JOB_ALIO]).isEqualTo(JobSourceApprovalStatus.READY)
            assertThat(approvalStatusByCode[JobSourceCode.CLEAN_EYE]).isEqualTo(JobSourceApprovalStatus.READY)
            assertThat(approvalStatusByCode[JobSourceCode.NARA_ILTEO]).isEqualTo(JobSourceApprovalStatus.READY)
            assertThat(approvalStatusByCode[JobSourceCode.SARAMIN]).isEqualTo(JobSourceApprovalStatus.PENDING_APPROVAL)
            assertThat(approvalStatusByCode[JobSourceCode.IBK_ONE_JOB]).isEqualTo(JobSourceApprovalStatus.UNAVAILABLE)
            assertThat(approvalStatusByCode[JobSourceCode.WORK24]).isEqualTo(JobSourceApprovalStatus.UNAVAILABLE)
        }

        @Test
        fun `source_code는 Unique Constraint로 중복 등록을 막는다`() {
            assertThat(
                runCatching {
                    jobSourceRepository.saveAndFlush(
                        JobSourceEntity(
                            sourceCode = JobSourceCode.MMA,
                            name = "중복 MMA",
                            sourceType = JobSourceType.EXTERNAL_API,
                            approvalStatus = JobSourceApprovalStatus.PENDING_APPROVAL,
                        ),
                    )
                }.isFailure,
            ).isTrue()
        }

        @Test
        fun `수집 실행 목록은 sourceId·status·기간 조건을 모두 만족하는 결과만 반환한다`() {
            val mma = jobSourceRepository.findBySourceCode(JobSourceCode.MMA)!!
            val saramin = jobSourceRepository.findBySourceCode(JobSourceCode.SARAMIN)!!

            persistRun(mma.id!!, CollectionRunStatus.SUCCESS, now.minusDays(2))
            persistRun(mma.id!!, CollectionRunStatus.FAILED, now.minusHours(1))
            persistRun(saramin.id!!, CollectionRunStatus.SUCCESS, now.minusHours(1))

            val mmaOnly =
                collectionRunRepository.search(mma.id, null, null, null, PageRequest.of(0, 20))
            assertThat(mmaOnly.content).hasSize(2)

            val mmaFailedOnly =
                collectionRunRepository.search(mma.id, CollectionRunStatus.FAILED, null, null, PageRequest.of(0, 20))
            assertThat(mmaFailedOnly.content).hasSize(1)
            assertThat(mmaFailedOnly.content.single().status).isEqualTo(CollectionRunStatus.FAILED)

            val recentOnly =
                collectionRunRepository.search(null, null, now.minusHours(2), null, PageRequest.of(0, 20))
            assertThat(recentOnly.content).hasSize(2)
        }

        @Test
        fun `이미 진행 중인 실행이 있으면 existsBySourceIdAndStatusIn이 true를 반환한다`() {
            val mma = jobSourceRepository.findBySourceCode(JobSourceCode.MMA)!!
            persistRun(mma.id!!, CollectionRunStatus.RUNNING, now)

            val running =
                collectionRunRepository.existsBySourceIdAndStatusIn(
                    mma.id!!,
                    listOf(CollectionRunStatus.PENDING, CollectionRunStatus.RUNNING),
                )
            val succeeded =
                collectionRunRepository.existsBySourceIdAndStatusIn(mma.id!!, listOf(CollectionRunStatus.SUCCESS))

            assertThat(running).isTrue()
            assertThat(succeeded).isFalse()
        }

        @Test
        fun `수집 실행 오류는 run_id로 발생 순서대로 조회된다`() {
            val mma = jobSourceRepository.findBySourceCode(JobSourceCode.MMA)!!
            val run = persistRun(mma.id!!, CollectionRunStatus.PARTIAL_SUCCESS, now)

            collectionRunErrorRepository.saveAndFlush(
                CollectionRunError(runId = run.id!!, code = "A", message = "먼저 발생", occurredAt = now),
            )
            collectionRunErrorRepository.saveAndFlush(
                CollectionRunError(runId = run.id!!, code = "B", message = "나중 발생", occurredAt = now.plusMinutes(1)),
            )

            val errors = collectionRunErrorRepository.findAllByRunIdOrderByOccurredAtAsc(run.id!!)

            assertThat(errors.map { it.code }).containsExactly("A", "B")
        }

        @Test
        fun `수집 실행이 삭제되면 오류도 함께 삭제된다`() {
            val mma = jobSourceRepository.findBySourceCode(JobSourceCode.MMA)!!
            val run = persistRun(mma.id!!, CollectionRunStatus.FAILED, now)
            collectionRunErrorRepository.saveAndFlush(
                CollectionRunError(runId = run.id!!, code = "A", message = "오류", occurredAt = now),
            )

            collectionRunRepository.delete(run)
            collectionRunRepository.flush()

            assertThat(collectionRunErrorRepository.findAllByRunIdOrderByOccurredAtAsc(run.id!!)).isEmpty()
        }

        private fun persistRun(
            sourceId: Long,
            status: CollectionRunStatus,
            startedAt: LocalDateTime,
        ): CollectionRun =
            collectionRunRepository.saveAndFlush(
                CollectionRun(
                    sourceId = sourceId,
                    action = CollectorAction.SYNC,
                    status = status,
                    startedAt = startedAt,
                ),
            )

        companion object {
            @Container
            @ServiceConnection
            @JvmStatic
            val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:18.4-alpine"))
        }
    }
