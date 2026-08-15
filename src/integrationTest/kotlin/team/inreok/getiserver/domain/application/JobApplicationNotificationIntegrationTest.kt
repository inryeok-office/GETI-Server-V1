package team.inreok.getiserver.domain.application

import com.redis.testcontainers.RedisContainer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.data.domain.PageRequest
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import team.inreok.getiserver.domain.application.dto.JobApplicationAdminAction
import team.inreok.getiserver.domain.application.dto.JobApplicationAdminActionRequest
import team.inreok.getiserver.domain.application.entity.JobApplication
import team.inreok.getiserver.domain.application.entity.type.JobApplicationStatus
import team.inreok.getiserver.domain.application.exception.ApplicationActionNotAvailableException
import team.inreok.getiserver.domain.application.repository.JobApplicationRepository
import team.inreok.getiserver.domain.application.service.JobApplicationAdminService
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
import team.inreok.getiserver.domain.notification.entity.Notification
import team.inreok.getiserver.domain.notification.entity.type.NotificationTargetType
import team.inreok.getiserver.domain.notification.entity.type.NotificationType
import team.inreok.getiserver.domain.notification.repository.NotificationRepository
import java.util.UUID

/**
 * Issue #135(Application Phase 7)이 발행하는 [team.inreok.getiserver.domain.application.event.JobApplicationReviewedEvent]가
 * 실제 사용자 Flow에서 `notifications` Row로 만들어지는지, 그리고 검토 Action이 Rollback되면
 * 알림도 만들어지지 않는지 실제 PostgreSQL Commit 경계로 검증한다.
 *
 * [DomainEventNotificationIntegrationTest][team.inreok.getiserver.domain.notification.DomainEventNotificationIntegrationTest]와
 * 같은 구조·이유를 따른다 -- Service Test(Mock)는 "Event를 발행했다"까지만,
 * [JobApplicationReviewedNotificationListenerTest][team.inreok.getiserver.domain.notification.event.JobApplicationReviewedNotificationListenerTest]는
 * "Event를 받으면 알림을 만든다"까지만 검증한다. 둘을 잇는
 * `@TransactionalEventListener(AFTER_COMMIT)` 배선과 Rollback 시 알림이 생기지 않는 일관성은 실제
 * Transaction 경계가 있어야 확인할 수 있어 이 Test에서만 검증된다(Issue #135 요구사항 "Docker 기반
 * 통합 테스트로 원본 Transaction Rollback 시 알림 미생성을 검증한다").
 *
 * 양식·자격 검증(Phase 1~3)은 이 Test의 관심사가 아니라 `JobApplication`을 직접 저장하고
 * `isDeveloper=true`로 호출해 담당 교사 판정(`requireManagerOrDeveloper`)을 우회한다
 * (`DomainEventNotificationIntegrationTest.createPublishedProgram`이 신청 Flow를 우회하는 것과
 * 동일한 판단) -- 확인하려는 것은 "검토 Action이 성공하면 지원자에게 알림이 만들어지는가"뿐이다.
 * 다만 `job_applications`의 `job_id`/`applicant_member_id`는 실제 FK 제약이 있어(V2 Migration)
 * Company/Job/Member는 실제 Row로 저장해야 한다.
 */
@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = [
        "app.jwt.secret=job-application-notification-integration-test-only-jwt-secret-value",
        "app.jwt.access-token-expiration-seconds=1800",
        "app.jwt.refresh-token-expiration-seconds=1209600",
        "app.file.storage.bucket=geti-integration-test",
        "app.file.storage.region=us-east-1",
        "app.file.storage.access-key=integration-test-only-access-key",
        "app.file.storage.secret-key=integration-test-only-secret-key",
    ],
)
class JobApplicationNotificationIntegrationTest {
    @Autowired
    private lateinit var jobApplicationAdminService: JobApplicationAdminService

    @Autowired
    private lateinit var jobApplicationRepository: JobApplicationRepository

    @Autowired
    private lateinit var jobRepository: JobRepository

    @Autowired
    private lateinit var companyRepository: CompanyRepository

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Autowired
    private lateinit var notificationRepository: NotificationRepository

    @Test
    fun `APPROVE 처리 후 지원자에게 JOB_APPLICATION_STATUS_CHANGED 알림이 생성된다`() {
        val studentId = createStudent("approve")
        val applicationId = createSubmittedApplication(studentId)

        jobApplicationAdminService.executeAction(
            applicationId,
            requesterMemberId = DEVELOPER_ID,
            isDeveloper = true,
            request = JobApplicationAdminActionRequest(JobApplicationAdminAction.APPROVE),
        )

        val notification = awaitSingleNotification(studentId)
        assertThat(notification.type).isEqualTo(NotificationType.JOB_APPLICATION_STATUS_CHANGED)
        assertThat(notification.targetType).isEqualTo(NotificationTargetType.JOB_APPLICATION)
        assertThat(notification.targetId).isEqualTo(applicationId)
        assertThat(notification.isRead).isFalse()
        assertThat(notification.content).contains("승인")
    }

    @Test
    fun `REQUEST_REVISION 처리 후 지원자에게 사유를 담은 알림이 생성된다`() {
        val studentId = createStudent("revision")
        val applicationId = createSubmittedApplication(studentId)

        jobApplicationAdminService.executeAction(
            applicationId,
            requesterMemberId = DEVELOPER_ID,
            isDeveloper = true,
            request =
                JobApplicationAdminActionRequest(
                    JobApplicationAdminAction.REQUEST_REVISION,
                    reason = "포트폴리오 링크를 추가해주세요.",
                ),
        )

        val notification = awaitSingleNotification(studentId)
        assertThat(notification.type).isEqualTo(NotificationType.JOB_APPLICATION_STATUS_CHANGED)
        assertThat(notification.content).contains("포트폴리오 링크를 추가해주세요.")
    }

    @Test
    fun `허용되지 않은 상태에서 검토 Action이 거부돼 Rollback되면 알림이 생성되지 않는다`() {
        // JOB_APPLICATION_ADMIN_ACTION_ALLOWED_FROM은 APPROVE를 SUBMITTED에서만 허용한다
        // (JobApplicationAdminTransition.kt). DRAFT 상태에 APPROVE를 시도하면 검증 단계에서
        // 거부되어 상태 전이·이력 기록·Event 발행 중 어느 것도 일어나지 않는다.
        val studentId = createStudent("rollback")
        val applicationId = createSubmittedApplication(studentId, status = JobApplicationStatus.DRAFT)

        assertThatThrownBy {
            jobApplicationAdminService.executeAction(
                applicationId,
                requesterMemberId = DEVELOPER_ID,
                isDeveloper = true,
                request = JobApplicationAdminActionRequest(JobApplicationAdminAction.APPROVE),
            )
        }.isInstanceOf(ApplicationActionNotAvailableException::class.java)

        // AFTER_COMMIT Listener는 Commit되지 않은(애초에 시작되지도 않은) 상태 전이에서는
        // 실행되지 않으므로 알림이 하나도 생기지 않아야 한다.
        assertNoNotificationAppears(studentId)
    }

    /**
     * 알림이 생길 때까지 짧게 Polling한다. 알림 생성이 `notificationTaskExecutor`에서 비동기로
     * 실행되므로 고정 Sleep 대신 조건이 만족되면 바로 진행해 Test 시간을 낭비하지 않는다
     * (`DomainEventNotificationIntegrationTest.awaitSingleNotification`과 동일한 이유).
     */
    private fun awaitSingleNotification(memberId: Long): Notification {
        val deadline = System.nanoTime() + AWAIT_TIMEOUT_MILLIS * NANOS_PER_MILLI
        var notifications = notificationsOf(memberId)
        while (notifications.isEmpty() && System.nanoTime() < deadline) {
            Thread.sleep(POLL_INTERVAL_MILLIS)
            notifications = notificationsOf(memberId)
        }
        assertThat(notifications).hasSize(1)
        return notifications.single()
    }

    /** 알림이 "생기지 않아야 한다"는 것은 Polling으로 증명할 수 없어, 충분한 시간을 기다린 뒤 확인한다. */
    private fun assertNoNotificationAppears(memberId: Long) {
        Thread.sleep(SETTLE_MILLIS)
        assertThat(notificationsOf(memberId)).isEmpty()
    }

    private fun notificationsOf(memberId: Long): List<Notification> =
        notificationRepository
            .findMyNotifications(
                memberId = memberId,
                isRead = null,
                type = null,
                pageable = PageRequest.of(0, PAGE_SIZE),
            ).content

    /** 매 Test마다 새 학생·공고·기업을 만들어 알림 Polling이 다른 Test와 섞이지 않게 한다. */
    private fun createStudent(subject: String): Long {
        val member =
            memberRepository.saveAndFlush(
                Member(
                    oauthProvider = OAuthProvider.DG,
                    oauthSubject = "job-application-notification-$subject-${UUID.randomUUID()}",
                    email = "job-application-notification-$subject-${UUID.randomUUID()}@example.com",
                    status = MemberStatus.ACTIVE,
                ),
            )
        return requireNotNull(member.id)
    }

    private fun createSubmittedApplication(
        studentMemberId: Long,
        status: JobApplicationStatus = JobApplicationStatus.SUBMITTED,
    ): Long {
        val jobId = createJob()
        val application =
            jobApplicationRepository.saveAndFlush(
                JobApplication(
                    jobId = jobId,
                    applicantMemberId = studentMemberId,
                    attemptNumber = 1,
                    contactEmail = "student-$studentMemberId@example.com",
                    answers = "[]",
                    status = status,
                ),
            )
        return requireNotNull(application.id)
    }

    private fun createJob(): Long {
        val company =
            companyRepository.saveAndFlush(
                Company(name = "알림 Test 기업 ${UUID.randomUUID()}", type = CompanyType.GENERAL),
            )
        val job =
            jobRepository.saveAndFlush(
                Job(
                    companyId = requireNotNull(company.id),
                    type = PostingType.GENERAL,
                    applicationMethod = ApplicationMethod.INTERNAL,
                    title = "알림 Test용 공고",
                    status = JobStatus.PUBLISHED,
                ),
            )
        return requireNotNull(job.id)
    }

    companion object {
        private const val PAGE_SIZE = 20
        private const val AWAIT_TIMEOUT_MILLIS = 5_000L
        private const val POLL_INTERVAL_MILLIS = 50L
        private const val SETTLE_MILLIS = 1_000L
        private const val NANOS_PER_MILLI = 1_000_000L
        private const val DEVELOPER_ID = 999L

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
