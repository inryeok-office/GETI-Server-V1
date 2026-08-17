package team.inreok.getiserver.domain.application

import com.redis.testcontainers.RedisContainer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import team.inreok.getiserver.domain.application.dto.JobApplicationAction
import team.inreok.getiserver.domain.application.dto.JobApplicationActionRequest
import team.inreok.getiserver.domain.application.dto.JobApplicationAdminAction
import team.inreok.getiserver.domain.application.dto.JobApplicationAdminActionRequest
import team.inreok.getiserver.domain.application.entity.JobApplication
import team.inreok.getiserver.domain.application.entity.type.JobApplicationStatus
import team.inreok.getiserver.domain.application.exception.ApplicationActionNotAvailableException
import team.inreok.getiserver.domain.application.repository.JobApplicationRepository
import team.inreok.getiserver.domain.application.service.JobApplicationAdminService
import team.inreok.getiserver.domain.application.service.JobApplicationService
import team.inreok.getiserver.domain.company.entity.Company
import team.inreok.getiserver.domain.company.entity.type.CompanyType
import team.inreok.getiserver.domain.company.repository.CompanyRepository
import team.inreok.getiserver.domain.job.entity.Job
import team.inreok.getiserver.domain.job.entity.type.ApplicationMethod
import team.inreok.getiserver.domain.job.entity.type.JobStatus
import team.inreok.getiserver.domain.job.entity.type.PostingType
import team.inreok.getiserver.domain.job.repository.JobRepository
import team.inreok.getiserver.domain.member.entity.Member
import team.inreok.getiserver.domain.member.entity.type.MemberStatus
import team.inreok.getiserver.domain.member.entity.type.OAuthProvider
import team.inreok.getiserver.domain.member.repository.MemberRepository
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Application Phase 11(Issue #139)의 동시성 시나리오다. PR #129/#130이 `findByIdForUpdate`
 * (`PESSIMISTIC_WRITE` Lock)를 구현했지만 실제 Docker/PostgreSQL 기반 동시성 검증은 작성하지
 * 않았다고 명시했던 공백을 메운다. 학생 Action(`JobApplicationService.executeAction`)과 교사
 * Action(`JobApplicationAdminService.executeAction`)이 같은 `findByIdForUpdate`로 같은 Row를
 * 잠그므로, 두 Service를 가로지르는 동시 요청도 실제로 직렬화되는지 여기서 확인한다.
 */
@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = [
        "app.jwt.secret=job-application-concurrency-integration-test-only-jwt-secret-value",
        "app.jwt.access-token-expiration-seconds=1800",
        "app.jwt.refresh-token-expiration-seconds=1209600",
        "app.file.storage.bucket=geti-integration-test",
        "app.file.storage.region=us-east-1",
        "app.file.storage.access-key=integration-test-only-access-key",
        "app.file.storage.secret-key=integration-test-only-secret-key",
    ],
)
class JobApplicationReviewConcurrencyIntegrationTest {
    @Autowired
    private lateinit var jobApplicationService: JobApplicationService

    @Autowired
    private lateinit var jobApplicationAdminService: JobApplicationAdminService

    @Autowired
    private lateinit var jobApplicationRepository: JobApplicationRepository

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Autowired
    private lateinit var companyRepository: CompanyRepository

    @Autowired
    private lateinit var jobRepository: JobRepository

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    private val transactionTemplate: TransactionTemplate by lazy { TransactionTemplate(transactionManager) }

    @Test
    fun `학생 WITHDRAW와 교사 APPROVE가 동시에 오면 하나만 성공하고 모순 상태가 남지 않는다`() {
        val teacherId = createMember("withdraw-vs-approve-teacher")
        val studentId = createMember("withdraw-vs-approve-student")
        val applicationId = createSubmittedApplication(teacherId, studentId)

        val executor = Executors.newFixedThreadPool(2)
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        try {
            val withdrawFuture =
                executor.submit(
                    Callable {
                        ready.countDown()
                        start.await()
                        runCatching {
                            jobApplicationService.executeAction(
                                applicationId,
                                studentId,
                                JobApplicationActionRequest(JobApplicationAction.WITHDRAW),
                            )
                        }
                    },
                )
            val approveFuture =
                executor.submit(
                    Callable {
                        ready.countDown()
                        start.await()
                        runCatching {
                            jobApplicationAdminService.executeAction(
                                applicationId,
                                teacherId,
                                isDeveloper = false,
                                JobApplicationAdminActionRequest(JobApplicationAdminAction.APPROVE),
                            )
                        }
                    },
                )

            assertThat(ready.await(AWAIT_SECONDS, TimeUnit.SECONDS)).isTrue()
            start.countDown()

            val withdrawResult = withdrawFuture.get(AWAIT_SECONDS, TimeUnit.SECONDS)
            val approveResult = approveFuture.get(AWAIT_SECONDS, TimeUnit.SECONDS)

            val results = listOf(withdrawResult, approveResult)
            assertThat(results.count { it.isSuccess }).isEqualTo(1)
            assertThat(results.count { it.isFailure }).isEqualTo(1)
            assertThat(results.mapNotNull { it.exceptionOrNull() }.single())
                .isInstanceOf(ApplicationActionNotAvailableException::class.java)

            // 최종 상태는 둘 중 하나여야 하고(먼저 Lock을 잡은 쪽이 이긴다), 두 전이가 모두 반영된
            // 모순 상태(예: WITHDRAWN이면서 APPROVED로 덮인 상태)는 있을 수 없다.
            val finalStatus = requireNotNull(jobApplicationRepository.findById(applicationId).orElse(null)).status
            assertThat(finalStatus).isIn(JobApplicationStatus.WITHDRAWN, JobApplicationStatus.APPROVED)
            assertThat(withdrawResult.isSuccess).isEqualTo(finalStatus == JobApplicationStatus.WITHDRAWN)
            assertThat(approveResult.isSuccess).isEqualTo(finalStatus == JobApplicationStatus.APPROVED)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `교사 A의 APPROVE와 교사 B의 REJECT가 동시에 오면 하나만 성공하고 모순 상태가 남지 않는다`() {
        val teacherAId = createMember("approve-vs-reject-teacher-a")
        val teacherBId = createMember("approve-vs-reject-teacher-b")
        val studentId = createMember("approve-vs-reject-student")
        // 두 교사 모두 이 공고를 담당(등록자/담당 교사)하도록 만들 필요는 없다 -- 둘 다
        // isDeveloper=true로 호출해 담당자 판정 자체가 아니라 Row Lock 직렬화만 확인한다.
        val applicationId = createSubmittedApplication(teacherAId, studentId)

        val executor = Executors.newFixedThreadPool(2)
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        try {
            val approveFuture =
                executor.submit(
                    Callable {
                        ready.countDown()
                        start.await()
                        runCatching {
                            jobApplicationAdminService.executeAction(
                                applicationId,
                                teacherAId,
                                isDeveloper = true,
                                JobApplicationAdminActionRequest(JobApplicationAdminAction.APPROVE),
                            )
                        }
                    },
                )
            val rejectFuture =
                executor.submit(
                    Callable {
                        ready.countDown()
                        start.await()
                        runCatching {
                            jobApplicationAdminService.executeAction(
                                applicationId,
                                teacherBId,
                                isDeveloper = true,
                                JobApplicationAdminActionRequest(JobApplicationAdminAction.REJECT, reason = "동시성 Test"),
                            )
                        }
                    },
                )

            assertThat(ready.await(AWAIT_SECONDS, TimeUnit.SECONDS)).isTrue()
            start.countDown()

            val approveResult = approveFuture.get(AWAIT_SECONDS, TimeUnit.SECONDS)
            val rejectResult = rejectFuture.get(AWAIT_SECONDS, TimeUnit.SECONDS)

            val results = listOf(approveResult, rejectResult)
            assertThat(results.count { it.isSuccess }).isEqualTo(1)
            assertThat(results.count { it.isFailure }).isEqualTo(1)
            assertThat(results.mapNotNull { it.exceptionOrNull() }.single())
                .isInstanceOf(ApplicationActionNotAvailableException::class.java)

            val finalStatus = requireNotNull(jobApplicationRepository.findById(applicationId).orElse(null)).status
            assertThat(finalStatus).isIn(JobApplicationStatus.APPROVED, JobApplicationStatus.REJECTED)
            assertThat(approveResult.isSuccess).isEqualTo(finalStatus == JobApplicationStatus.APPROVED)
            assertThat(rejectResult.isSuccess).isEqualTo(finalStatus == JobApplicationStatus.REJECTED)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `findByIdForUpdate Lock을 먼저 잡은 Transaction이 끝날 때까지 두 번째 요청이 실제로 대기한다`() {
        // InquiryAnswerConcurrencyIntegrationTest와 같은 패턴이다 -- 한 Thread가 Lock을 잡은 채
        // 일부러 붙잡아 두고(lockAcquired로 신호), 그동안 두 번째 요청이 끝나지 않았음을 직접
        // 확인한다. 앞의 두 Test는 "누가 이기든 하나만 성공한다"만 보이지만, 이 Test는 "잡고
        // 있는 동안 실제로 막힌다"는 인과관계 자체를 확인한다.
        val teacherId = createMember("lock-block-teacher")
        val studentId = createMember("lock-block-student")
        val applicationId = createSubmittedApplication(teacherId, studentId)

        val lockAcquired = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val holderFuture =
                executor.submit {
                    transactionTemplate.executeWithoutResult {
                        val locked = requireNotNull(jobApplicationRepository.findByIdForUpdate(applicationId))
                        lockAcquired.countDown()
                        Thread.sleep(LOCK_HOLD_MILLIS)
                        locked.status = JobApplicationStatus.APPROVED
                        jobApplicationRepository.flush()
                    }
                }

            assertThat(lockAcquired.await(AWAIT_SECONDS, TimeUnit.SECONDS)).isTrue()

            val secondRequestStartedAt = System.nanoTime()
            val secondFuture =
                executor.submit(
                    Callable {
                        jobApplicationAdminService.executeAction(
                            applicationId,
                            teacherId,
                            isDeveloper = false,
                            JobApplicationAdminActionRequest(JobApplicationAdminAction.REJECT, reason = "Lock 대기 Test"),
                        )
                    },
                )

            holderFuture.get(AWAIT_SECONDS, TimeUnit.SECONDS)
            // 두 번째 요청은 REJECT를 시도하지만, 첫 Transaction이 Commit한 뒤에는 상태가 이미
            // APPROVED라 requireAllowedAdminTransition이 거부한다 -- 이 예외 자체가 두 번째
            // 요청이 "Lock을 기다렸다가" 갱신된 상태를 읽었다는 증거다(Lock 없이 갱신 전 상태를
            // 읽었다면 REJECT가 그대로 성공했을 것이다).
            org.assertj.core.api.Assertions
                .assertThatThrownBy { secondFuture.get(AWAIT_SECONDS, TimeUnit.SECONDS) }
                .hasCauseInstanceOf(ApplicationActionNotAvailableException::class.java)
            val elapsedMillis = (System.nanoTime() - secondRequestStartedAt) / NANOS_PER_MILLI
            assertThat(elapsedMillis)
                .`as`("두 번째 요청이 첫 Transaction의 Lock 보유 시간만큼 대기하지 않았다")
                .isGreaterThanOrEqualTo(LOCK_HOLD_MILLIS - LOCK_WAIT_TOLERANCE_MILLIS)

            assertThat(requireNotNull(jobApplicationRepository.findById(applicationId).orElse(null)).status)
                .isEqualTo(JobApplicationStatus.APPROVED)
        } finally {
            executor.shutdownNow()
        }
    }

    /**
     * 양식·자격 검증(Phase 1~3)은 이 Test의 관심사가 아니라 `JobApplication`을 SUBMITTED로 직접
     * 저장한다(`JobApplicationNotificationIntegrationTest.createApplication`과 동일한 판단) --
     * 확인하려는 것은 "동시 요청이 Row Lock으로 직렬화되는가"뿐이다. `job_applications.job_id`는
     * 실제 FK 제약이 있어(V2 Migration) Company/Job은 실제 Row로 저장한다. [teacherId]를 공고의
     * 등록자·담당 교사로 지정해 `requireManagerOrDeveloper`가 실제 담당자 판정 경로를 탄다.
     */
    private fun createSubmittedApplication(
        teacherId: Long,
        studentId: Long,
    ): Long {
        val company =
            companyRepository.saveAndFlush(
                Company(name = "동시성 Test 기업 ${UUID.randomUUID()}", type = CompanyType.GENERAL),
            )
        val job =
            jobRepository.saveAndFlush(
                Job(
                    companyId = requireNotNull(company.id),
                    type = PostingType.GENERAL,
                    applicationMethod = ApplicationMethod.INTERNAL,
                    title = "동시성 Test 공고",
                    status = JobStatus.PUBLISHED,
                ).apply {
                    createdByMemberId = teacherId
                    managerMemberId = teacherId
                },
            )
        val application =
            jobApplicationRepository.saveAndFlush(
                JobApplication(
                    jobId = requireNotNull(job.id),
                    applicantMemberId = studentId,
                    attemptNumber = 1,
                    contactEmail = "student-$studentId@example.com",
                    answers = "[]",
                    status = JobApplicationStatus.SUBMITTED,
                ),
            )
        return requireNotNull(application.id)
    }

    private fun createMember(subject: String): Long {
        val member =
            memberRepository.saveAndFlush(
                Member(
                    oauthProvider = OAuthProvider.DG,
                    oauthSubject = "concurrency-$subject-${UUID.randomUUID()}",
                    email = "concurrency-$subject-${UUID.randomUUID()}@example.com",
                    status = MemberStatus.ACTIVE,
                ),
            )
        return requireNotNull(member.id)
    }

    companion object {
        private const val AWAIT_SECONDS = 10L
        private const val LOCK_HOLD_MILLIS = 1_500L
        private const val LOCK_WAIT_TOLERANCE_MILLIS = 300L
        private const val NANOS_PER_MILLI = 1_000_000L

        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:18.4-alpine"))

        @Container
        @ServiceConnection
        @JvmStatic
        val redis = RedisContainer(DockerImageName.parse("redis:8.8.1-alpine"))
    }
}
