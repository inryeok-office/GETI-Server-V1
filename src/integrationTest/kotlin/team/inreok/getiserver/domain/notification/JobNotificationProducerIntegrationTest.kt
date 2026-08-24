package team.inreok.getiserver.domain.notification

import com.redis.testcontainers.RedisContainer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.data.domain.PageRequest
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import team.inreok.getiserver.domain.company.entity.Company
import team.inreok.getiserver.domain.company.entity.type.CompanyType
import team.inreok.getiserver.domain.company.repository.CompanyRepository
import team.inreok.getiserver.domain.job.dto.JobCreateRequest
import team.inreok.getiserver.domain.job.dto.JobStatusUpdateRequest
import team.inreok.getiserver.domain.job.entity.type.ApplicationMethod
import team.inreok.getiserver.domain.job.entity.type.JobStatus
import team.inreok.getiserver.domain.job.entity.type.PostingType
import team.inreok.getiserver.domain.job.service.JobService
import team.inreok.getiserver.domain.member.entity.Member
import team.inreok.getiserver.domain.member.entity.MemberRole
import team.inreok.getiserver.domain.member.entity.MemberRoleId
import team.inreok.getiserver.domain.member.entity.type.AcademicStatus
import team.inreok.getiserver.domain.member.entity.type.MemberStatus
import team.inreok.getiserver.domain.member.entity.type.OAuthProvider
import team.inreok.getiserver.domain.member.entity.type.RoleType
import team.inreok.getiserver.domain.member.repository.MemberRepository
import team.inreok.getiserver.domain.member.repository.MemberRoleRepository
import team.inreok.getiserver.domain.notification.entity.Notification
import team.inreok.getiserver.domain.notification.entity.type.NotificationTargetType
import team.inreok.getiserver.domain.notification.entity.type.NotificationType
import team.inreok.getiserver.domain.notification.repository.NotificationRepository
import team.inreok.getiserver.domain.recommendation.entity.MemberJobPreference
import team.inreok.getiserver.domain.recommendation.repository.MemberJobPreferenceRepository
import java.util.UUID

/**
 * `JOB_PUBLISHED`/`JOB_CLOSED` Producer(Issue #191)가 실제 Job 게시·마감 Flow에서
 * `notifications` Row로 만들어지는지, 그리고 대상 조건을 충족하지 않는 회원은 알림을 받지
 * 않는지를 실제 PostgreSQL Commit 경계로 검증한다. [DomainEventNotificationIntegrationTest]와
 * 같은 목적·구조를 Job 쪽에서 담당한다(Program 쪽은 그 Test가 이미 담당).
 */
@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = [
        "app.jwt.secret=job-notification-producer-integration-test-only-jwt-secret-value",
        "app.jwt.access-token-expiration-seconds=1800",
        "app.jwt.refresh-token-expiration-seconds=1209600",
        "app.file.storage.bucket=geti-integration-test",
        "app.file.storage.region=us-east-1",
        "app.file.storage.access-key=integration-test-only-access-key",
        "app.file.storage.secret-key=integration-test-only-secret-key",
    ],
)
class JobNotificationProducerIntegrationTest {
    @Autowired
    private lateinit var jobService: JobService

    @Autowired
    private lateinit var companyRepository: CompanyRepository

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Autowired
    private lateinit var memberRoleRepository: MemberRoleRepository

    @Autowired
    private lateinit var memberJobPreferenceRepository: MemberJobPreferenceRepository

    @Autowired
    private lateinit var notificationRepository: NotificationRepository

    @Test
    fun `대상 학년으로 게시된 공고는 같은 학년 재학생에게만 JOB_PUBLISHED 알림을 생성한다`() {
        val teacherId = createTeacher()
        val targetGradeStudentId = createStudent(grade = 3)
        val otherGradeStudentId = createStudent(grade = 2)
        val graduatedStudentId = createStudent(grade = 3, academicStatus = AcademicStatus.GRADUATED)
        val companyId = createCompany()

        val jobId =
            jobService
                .create(
                    publishedJobRequest(companyId, targetGrade = 3),
                    createdByMemberId = teacherId,
                ).jobId

        val notification = awaitSingleNotification(targetGradeStudentId)
        assertThat(notification.type).isEqualTo(NotificationType.JOB_PUBLISHED)
        assertThat(notification.targetType).isEqualTo(NotificationTargetType.JOB)
        assertThat(notification.targetId).isEqualTo(jobId)
        assertThat(notification.content).contains("게시 알림 Test용 채용공고")

        assertNoNotificationAppears(otherGradeStudentId)
        assertNoNotificationAppears(graduatedStudentId)
    }

    @Test
    fun `전 학년 대상으로 게시된 공고는 학년과 무관하게 재학생 모두에게 JOB_PUBLISHED 알림을 생성한다`() {
        val teacherId = createTeacher()
        val studentId = createStudent(grade = 1)
        val companyId = createCompany()

        jobService.create(publishedJobRequest(companyId, targetGrade = null), createdByMemberId = teacherId)

        awaitSingleNotification(studentId)
    }

    @Test
    fun `공고를 마감하면 그 공고를 북마크한 회원에게만 JOB_CLOSED 알림을 생성한다`() {
        val teacherId = createTeacher()
        val bookmarkerId = createStudent(grade = 1)
        val nonBookmarkerId = createStudent(grade = 1)
        val companyId = createCompany()

        val jobId =
            jobService.create(publishedJobRequest(companyId, targetGrade = null), createdByMemberId = teacherId).jobId
        // JOB_PUBLISHED 알림이 먼저 만들어지므로, JOB_CLOSED만 검증할 수 있도록 두 학생 모두의
        // 게시 알림을 먼저 기다려 비운다.
        awaitSingleNotification(bookmarkerId)
        awaitSingleNotification(nonBookmarkerId)

        memberJobPreferenceRepository.saveAndFlush(
            MemberJobPreference(memberId = bookmarkerId, jobId = jobId, bookmarked = true),
        )

        jobService.changeStatus(jobId, JobStatusUpdateRequest(status = JobStatus.CLOSED), requesterId = teacherId)

        val notification = awaitSingleNotification(bookmarkerId)
        assertThat(notification.type).isEqualTo(NotificationType.JOB_CLOSED)
        assertThat(notification.targetId).isEqualTo(jobId)

        assertNoNotificationAppears(nonBookmarkerId)
    }

    private fun publishedJobRequest(
        companyId: Long,
        targetGrade: Int?,
    ) = JobCreateRequest(
        companyId = companyId,
        postingType = PostingType.GENERAL,
        applicationMethod = ApplicationMethod.EXTERNAL,
        title = "게시 알림 Test용 채용공고",
        status = JobStatus.PUBLISHED,
        content = "## 모집 부문\n- 백엔드 개발자",
        externalUrl = "https://example.com/apply",
        targetGrade = targetGrade,
    )

    private fun awaitSingleNotification(memberId: Long): Notification {
        val deadline = System.nanoTime() + AWAIT_TIMEOUT_MILLIS * NANOS_PER_MILLI
        var notifications = notificationsOf(memberId)
        while (notifications.isEmpty() && System.nanoTime() < deadline) {
            Thread.sleep(POLL_INTERVAL_MILLIS)
            notifications = notificationsOf(memberId)
        }
        assertThat(notifications).hasSize(1)
        val notification = notifications.single()
        notificationRepository.delete(notification)
        notificationRepository.flush()
        return notification
    }

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

    private fun createTeacher(): Long = createMember("job-notification-teacher", role = RoleType.TEACHER)

    private fun createStudent(
        grade: Int,
        academicStatus: AcademicStatus = AcademicStatus.ENROLLED,
    ): Long =
        createMember(
            "job-notification-student",
            role = RoleType.STUDENT,
            grade = grade,
            academicStatus = academicStatus,
        )

    private fun createMember(
        subjectPrefix: String,
        role: RoleType,
        grade: Int? = null,
        academicStatus: AcademicStatus? = null,
    ): Long {
        val subject = "$subjectPrefix-${UUID.randomUUID()}"
        val member =
            memberRepository.saveAndFlush(
                Member(
                    oauthProvider = OAuthProvider.DG,
                    oauthSubject = subject,
                    email = "$subject@example.com",
                    status = MemberStatus.ACTIVE,
                ).apply {
                    name = subjectPrefix
                    this.grade = grade
                    this.academicStatus = academicStatus
                },
            )
        val memberId = requireNotNull(member.id)
        memberRoleRepository.saveAndFlush(MemberRole(MemberRoleId(memberId = memberId, role = role)))
        return memberId
    }

    private fun createCompany(): Long =
        requireNotNull(
            companyRepository
                .saveAndFlush(Company(name = "인력개발원-${UUID.randomUUID()}", type = CompanyType.GENERAL))
                .id,
        )

    companion object {
        private const val PAGE_SIZE = 20
        private const val AWAIT_TIMEOUT_MILLIS = 5_000L
        private const val POLL_INTERVAL_MILLIS = 50L
        private const val SETTLE_MILLIS = 1_000L
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
