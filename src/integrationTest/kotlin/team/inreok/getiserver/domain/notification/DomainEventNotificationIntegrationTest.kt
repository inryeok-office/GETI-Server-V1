package team.inreok.getiserver.domain.notification

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
import team.inreok.getiserver.domain.member.dto.ApprovalAction
import team.inreok.getiserver.domain.member.dto.MemberApprovalRequest
import team.inreok.getiserver.domain.member.entity.Member
import team.inreok.getiserver.domain.member.entity.MemberRole
import team.inreok.getiserver.domain.member.entity.MemberRoleId
import team.inreok.getiserver.domain.member.entity.type.AcademicStatus
import team.inreok.getiserver.domain.member.entity.type.MemberStatus
import team.inreok.getiserver.domain.member.entity.type.OAuthProvider
import team.inreok.getiserver.domain.member.entity.type.RoleType
import team.inreok.getiserver.domain.member.exception.MemberNotPendingException
import team.inreok.getiserver.domain.member.repository.MemberRepository
import team.inreok.getiserver.domain.member.repository.MemberRoleRepository
import team.inreok.getiserver.domain.member.service.MemberApprovalService
import team.inreok.getiserver.domain.notification.entity.Notification
import team.inreok.getiserver.domain.notification.entity.type.NotificationTargetType
import team.inreok.getiserver.domain.notification.entity.type.NotificationType
import team.inreok.getiserver.domain.notification.repository.NotificationRepository
import team.inreok.getiserver.domain.program.dto.ProgramApplicationActionRequest
import team.inreok.getiserver.domain.program.dto.ProgramCreateRequest
import team.inreok.getiserver.domain.program.dto.ProgramStatusUpdateRequest
import team.inreok.getiserver.domain.program.entity.Program
import team.inreok.getiserver.domain.program.entity.ProgramApplication
import team.inreok.getiserver.domain.program.entity.type.ProgramApplicationAction
import team.inreok.getiserver.domain.program.entity.type.ProgramApplicationStatus
import team.inreok.getiserver.domain.program.entity.type.ProgramStatus
import team.inreok.getiserver.domain.program.entity.type.ProgramType
import team.inreok.getiserver.domain.program.exception.ProgramManageForbiddenException
import team.inreok.getiserver.domain.program.repository.ProgramApplicationRepository
import team.inreok.getiserver.domain.program.repository.ProgramRepository
import team.inreok.getiserver.domain.program.service.ProgramService
import java.time.LocalDateTime
import java.util.UUID

/**
 * 제품 계약 Notification §16.1이 요구하는 최소 인앱 알림 두 가지(교직원 가입 승인/거절, Program
 * 삭제)가 실제 사용자 Flow에서 `notifications` Row로 만들어지는지 실제 PostgreSQL Commit 경계로
 * 검증한다(Issue #118).
 *
 * Service Test(Mock)는 "Event를 발행했다"까지만, Listener Test(Mock)는 "Event를 받으면 알림을
 * 만든다"까지만 검증한다. 둘을 잇는 `@TransactionalEventListener(AFTER_COMMIT)` 배선과 Rollback
 * 시 알림이 생기지 않는 일관성은 실제 Transaction 경계가 있어야 확인할 수 있어 이 Test에서만
 * 검증된다.
 *
 * 알림 생성은 전용 `notificationTaskExecutor`에서 비동기로 실행되므로(PR #128 리뷰 지적), 생성을
 * 기대하는 Test는 [awaitNotifications]로 기다리고, 생성되지 않아야 하는 Test는
 * [assertNoNotificationAppears]로 잠시 기다린 뒤에도 비어 있음을 확인한다. 곧바로 조회하면
 * 아직 실행되지 않은 Task 때문에 양쪽 모두 잘못된 결과를 낼 수 있다.
 */
@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = [
        "app.jwt.secret=domain-event-notification-integration-test-only-jwt-secret-value",
        "app.jwt.access-token-expiration-seconds=1800",
        "app.jwt.refresh-token-expiration-seconds=1209600",
        "app.file.storage.bucket=geti-integration-test",
        "app.file.storage.region=us-east-1",
        "app.file.storage.access-key=integration-test-only-access-key",
        "app.file.storage.secret-key=integration-test-only-secret-key",
        // PROGRAM_PUBLISHED Test가 programService.create()로 실제 게시를 거쳐야 해 허용 채널을
        // 하나 구성한다(ProgramServiceImpl.requireAllowedDiscordChannelId). 값 자체는 임의의
        // Test 전용 Snowflake다.
        "app.discord.channel-policy.channels.program-notice.channel-id=1234567890123456",
    ],
)
class DomainEventNotificationIntegrationTest {
    @Autowired
    private lateinit var memberApprovalService: MemberApprovalService

    @Autowired
    private lateinit var programService: ProgramService

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Autowired
    private lateinit var memberRoleRepository: MemberRoleRepository

    @Autowired
    private lateinit var programRepository: ProgramRepository

    @Autowired
    private lateinit var programApplicationRepository: ProgramApplicationRepository

    @Autowired
    private lateinit var notificationRepository: NotificationRepository

    @Test
    fun `교직원 승인 처리 후 대상 회원에게 MEMBER_APPROVAL_RESULT 알림이 생성된다`() {
        val memberId = createPendingGoogleTeacher()

        memberApprovalService.process(memberId, MemberApprovalRequest(action = ApprovalAction.APPROVE))

        val notification = awaitSingleNotification(memberId)
        assertThat(notification.type).isEqualTo(NotificationType.MEMBER_APPROVAL_RESULT)
        assertThat(notification.targetType).isEqualTo(NotificationTargetType.MEMBER_APPROVAL)
        assertThat(notification.targetId).isEqualTo(memberId)
        assertThat(notification.isRead).isFalse()
        assertThat(notification.content).contains("승인")
    }

    @Test
    fun `교직원 거절 처리 후 대상 회원에게 거절 사유를 담은 알림이 생성된다`() {
        val memberId = createPendingGoogleTeacher()

        memberApprovalService.process(
            memberId,
            MemberApprovalRequest(action = ApprovalAction.REJECT, reason = "재직 증명 자료 미확인"),
        )

        val notification = awaitSingleNotification(memberId)
        assertThat(notification.type).isEqualTo(NotificationType.MEMBER_APPROVAL_RESULT)
        assertThat(notification.content).contains("재직 증명 자료 미확인")
    }

    @Test
    fun `승인 처리가 실패해 Rollback되면 알림이 생성되지 않는다`() {
        val memberId = createPendingGoogleTeacher()
        // 첫 처리로 PENDING을 벗어나게 한 뒤 같은 회원을 다시 승인하면 409로 거부되고 Rollback된다.
        memberApprovalService.process(memberId, MemberApprovalRequest(action = ApprovalAction.APPROVE))
        awaitSingleNotification(memberId)

        assertThatThrownBy {
            memberApprovalService.process(memberId, MemberApprovalRequest(action = ApprovalAction.APPROVE))
        }.isInstanceOf(MemberNotPendingException::class.java)

        // AFTER_COMMIT Listener는 Commit되지 않은 Transaction에서는 실행되지 않으므로 첫 승인의
        // 알림 1건에서 늘어나지 않아야 한다.
        Thread.sleep(SETTLE_MILLIS)
        assertThat(notificationsOf(memberId)).hasSize(1)
    }

    @Test
    fun `신청자가 있는 Program을 삭제하면 활성 신청자에게만 PROGRAM_DELETED 알림이 생성된다`() {
        val teacherId = requireNotNull(createMember("program-delete-teacher").id)
        val applicantId = requireNotNull(createMember("program-delete-applicant").id)
        val canceledApplicantId = requireNotNull(createMember("program-delete-canceled").id)
        val programId = createPublishedProgram(teacherId)
        saveApplication(programId, applicantId, ProgramApplicationStatus.APPLIED)
        saveApplication(programId, canceledApplicantId, ProgramApplicationStatus.CANCELED)

        programService.changeStatus(
            programId,
            requesterMemberId = teacherId,
            isDeveloper = false,
            request = ProgramStatusUpdateRequest(status = ProgramStatus.DELETED),
        )

        val notification = awaitSingleNotification(applicantId)
        assertThat(notification.type).isEqualTo(NotificationType.PROGRAM_DELETED)
        assertThat(notification.targetType).isEqualTo(NotificationTargetType.PROGRAM)
        assertThat(notification.targetId).isEqualTo(programId)
        assertThat(notification.content).contains("삭제 알림 Test용 특강")

        // 취소한 신청자는 알림 대상이 아니다.
        assertNoNotificationAppears(canceledApplicantId)
    }

    @Test
    fun `삭제 처리가 실패해 Rollback되면 신청자에게 알림이 생성되지 않는다`() {
        val teacherId = requireNotNull(createMember("program-delete-fail-teacher").id)
        val otherTeacherId = requireNotNull(createMember("program-delete-fail-other").id)
        val applicantId = requireNotNull(createMember("program-delete-fail-applicant").id)
        val programId = createPublishedProgram(teacherId)
        saveApplication(programId, applicantId, ProgramApplicationStatus.APPLIED)

        // 담당자가 아닌 교사의 삭제 요청은 403으로 거부되고 Rollback된다.
        assertThatThrownBy {
            programService.changeStatus(
                programId,
                requesterMemberId = otherTeacherId,
                isDeveloper = false,
                request = ProgramStatusUpdateRequest(status = ProgramStatus.DELETED),
            )
        }.isInstanceOf(ProgramManageForbiddenException::class.java)

        assertNoNotificationAppears(applicantId)
    }

    @Test
    fun `대상 학년으로 게시된 프로그램은 같은 학년 재학생에게만 PROGRAM_PUBLISHED 알림을 생성한다`() {
        val teacherId = requireNotNull(createMember("program-published-teacher").id)
        val targetGradeStudentId = createStudentMember("program-published-target", grade = 2)
        val otherGradeStudentId = createStudentMember("program-published-other", grade = 1)

        val response =
            programService.create(
                ProgramCreateRequest(
                    title = "게시 알림 Test용 캠프",
                    programType = ProgramType.SPECIAL_LECTURE,
                    status = ProgramStatus.PUBLISHED,
                    content = "## 모집 안내",
                    startAt = LocalDateTime.of(2026, 9, 1, 10, 0),
                    endAt = LocalDateTime.of(2026, 9, 1, 17, 0),
                    applicationStartAt = LocalDateTime.of(2026, 8, 1, 0, 0),
                    applicationEndAt = LocalDateTime.of(2026, 8, 20, 23, 59),
                    location = "본관 2층 대강당",
                    targetGrades = listOf(2),
                    discordChannelId = "1234567890123456",
                ),
                createdByMemberId = teacherId,
            )

        val notification = awaitSingleNotification(targetGradeStudentId)
        assertThat(notification.type).isEqualTo(NotificationType.PROGRAM_PUBLISHED)
        assertThat(notification.targetType).isEqualTo(NotificationTargetType.PROGRAM)
        assertThat(notification.targetId).isEqualTo(response.programId)
        assertThat(notification.content).contains("게시 알림 Test용 캠프")

        assertNoNotificationAppears(otherGradeStudentId)
    }

    @Test
    fun `프로그램 신청이 접수되면 신청 학생 본인과 담당 교사에게 PROGRAM_APPLICATION_APPLIED 알림을 생성한다`() {
        val teacherId = requireNotNull(createMember("program-applied-teacher").id)
        val studentId = createStudentMember("program-applied-student", grade = 1)
        val programId = createPublishedProgram(teacherId, title = "신청 알림 Test용 캠프")

        programService.executeApplicationAction(
            programId,
            studentMemberId = studentId,
            request = ProgramApplicationActionRequest(action = ProgramApplicationAction.APPLY),
        )

        val studentNotification = awaitSingleNotification(studentId)
        assertThat(studentNotification.type).isEqualTo(NotificationType.PROGRAM_APPLICATION_APPLIED)
        assertThat(studentNotification.targetId).isEqualTo(programId)
        assertThat(studentNotification.content).contains("신청 알림 Test용 캠프")

        val teacherNotification = awaitSingleNotification(teacherId)
        assertThat(teacherNotification.type).isEqualTo(NotificationType.PROGRAM_APPLICATION_APPLIED)
        assertThat(teacherNotification.content).contains("신청 알림 Test용 캠프")
    }

    @Test
    fun `프로그램 신청을 취소하면 취소 학생 본인과 담당 교사에게 PROGRAM_APPLICATION_CANCELED 알림을 생성한다`() {
        val teacherId = requireNotNull(createMember("program-canceled-teacher").id)
        val studentId = createStudentMember("program-canceled-student", grade = 1)
        val programId = createPublishedProgram(teacherId, title = "취소 알림 Test용 캠프")
        programService.executeApplicationAction(
            programId,
            studentMemberId = studentId,
            request = ProgramApplicationActionRequest(action = ProgramApplicationAction.APPLY),
        )
        // APPLY가 만든 PROGRAM_APPLICATION_APPLIED 알림(각 1건)이 먼저 만들어질 때까지 기다린다 --
        // CANCEL 알림은 이 알림들과 별개로 쌓이므로 지우지 않고 개수 증가로 구분한다.
        awaitNotificationCount(studentId, 1)
        awaitNotificationCount(teacherId, 1)

        programService.executeApplicationAction(
            programId,
            studentMemberId = studentId,
            request = ProgramApplicationActionRequest(action = ProgramApplicationAction.CANCEL),
        )

        // findMyNotifications는 최신순(createdAt DESC)이라 첫 항목이 방금 만들어진 CANCEL 알림이다.
        val studentNotification = awaitNotificationCount(studentId, 2).first()
        assertThat(studentNotification.type).isEqualTo(NotificationType.PROGRAM_APPLICATION_CANCELED)
        assertThat(studentNotification.targetId).isEqualTo(programId)

        val teacherNotification = awaitNotificationCount(teacherId, 2).first()
        assertThat(teacherNotification.type).isEqualTo(NotificationType.PROGRAM_APPLICATION_CANCELED)
    }

    /**
     * 알림이 생길 때까지 짧게 Polling한다. 알림 생성이 `notificationTaskExecutor`에서 비동기로
     * 실행되므로 고정 Sleep 대신 조건이 만족되면 바로 진행해 Test 시간을 낭비하지 않는다.
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

    /**
     * [expectedCount]에 도달할 때까지 짧게 Polling한다. 한 회원에게 같은 Test 안에서 알림이
     * 여러 번(예: 신청 후 취소) 생기는 경우, 매번 새 Row를 지우지 않고 누적 개수로 최신 알림을
     * 구분하기 위해 [awaitSingleNotification]과 별도로 둔다.
     */
    private fun awaitNotificationCount(
        memberId: Long,
        expectedCount: Int,
    ): List<Notification> {
        val deadline = System.nanoTime() + AWAIT_TIMEOUT_MILLIS * NANOS_PER_MILLI
        var notifications = notificationsOf(memberId)
        while (notifications.size < expectedCount && System.nanoTime() < deadline) {
            Thread.sleep(POLL_INTERVAL_MILLIS)
            notifications = notificationsOf(memberId)
        }
        assertThat(notifications).hasSize(expectedCount)
        return notifications
    }

    /**
     * 알림이 "생기지 않아야 한다"는 것은 Polling으로 증명할 수 없어, 비동기 Task가 실행되기에
     * 충분한 시간을 기다린 뒤에도 비어 있는지 확인한다.
     */
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

    private fun createPendingGoogleTeacher(): Long {
        val subject = "approval-notification-${UUID.randomUUID()}"
        val member =
            memberRepository.saveAndFlush(
                Member(
                    oauthProvider = OAuthProvider.GOOGLE,
                    oauthSubject = subject,
                    email = "$subject@example.com",
                    status = MemberStatus.PENDING,
                ),
            )
        return requireNotNull(member.id)
    }

    private fun createMember(subject: String): Member =
        memberRepository.saveAndFlush(
            Member(
                oauthProvider = OAuthProvider.DG,
                oauthSubject = "$subject-${UUID.randomUUID()}",
                email = "$subject-${UUID.randomUUID()}@example.com",
                status = MemberStatus.ACTIVE,
            ).apply { name = subject },
        )

    /**
     * PROGRAM_PUBLISHED Test가 [team.inreok.getiserver.domain.member.query.NotificationAudienceQueryPort]로
     * 조회할 재학생을 만든다 -- STUDENT Role과 ENROLLED 학적 상태, 학년까지 모두 채워야 그 Port의
     * 대상 조건(ACTIVE + STUDENT + ENROLLED (+ 학년))을 만족한다
     * (RecommendationAudienceQueryIntegrationTest의 studentOf와 동일한 이유).
     */
    private fun createStudentMember(
        subject: String,
        grade: Int,
    ): Long {
        val member =
            memberRepository.saveAndFlush(
                Member(
                    oauthProvider = OAuthProvider.DG,
                    oauthSubject = "$subject-${UUID.randomUUID()}",
                    email = "$subject-${UUID.randomUUID()}@example.com",
                    status = MemberStatus.ACTIVE,
                ).apply {
                    name = subject
                    academicStatus = AcademicStatus.ENROLLED
                    this.grade = grade
                },
            )
        val memberId = requireNotNull(member.id)
        memberRoleRepository.saveAndFlush(MemberRole(MemberRoleId(memberId = memberId, role = RoleType.STUDENT)))
        return memberId
    }

    /**
     * 신청 Flow(자격 검증·정원)는 이 Test의 관심사가 아니라 Program과 신청 Row를 직접 저장한다.
     * 확인하려는 것은 "이 시점에 활성 신청자·담당 교사에게 알림이 만들어지는가"뿐이다.
     */
    private fun createPublishedProgram(
        teacherId: Long,
        title: String = "삭제 알림 Test용 특강",
    ): Long {
        val program =
            programRepository.saveAndFlush(
                Program(
                    createdByMemberId = teacherId,
                    type = ProgramType.SPECIAL_LECTURE,
                    title = title,
                    status = ProgramStatus.PUBLISHED,
                ).apply { managerMemberId = teacherId },
            )
        return requireNotNull(program.id)
    }

    private fun saveApplication(
        programId: Long,
        applicantMemberId: Long,
        status: ProgramApplicationStatus,
    ) {
        programApplicationRepository.saveAndFlush(
            ProgramApplication(programId = programId, applicantMemberId = applicantMemberId, status = status),
        )
    }

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
