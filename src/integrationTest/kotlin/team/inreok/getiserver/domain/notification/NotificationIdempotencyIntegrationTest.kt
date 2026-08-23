package team.inreok.getiserver.domain.notification

import com.redis.testcontainers.RedisContainer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import team.inreok.getiserver.domain.member.entity.Member
import team.inreok.getiserver.domain.member.entity.type.MemberStatus
import team.inreok.getiserver.domain.member.entity.type.OAuthProvider
import team.inreok.getiserver.domain.member.repository.MemberRepository
import team.inreok.getiserver.domain.notification.dto.NotificationCreateCommand
import team.inreok.getiserver.domain.notification.entity.Notification
import team.inreok.getiserver.domain.notification.entity.type.NotificationType
import team.inreok.getiserver.domain.notification.repository.NotificationRepository
import team.inreok.getiserver.domain.notification.service.NotificationService
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Notification Idempotency(Issue #193)가 실제 PostgreSQL UNIQUE 제약
 * (`uk_notifications_recipient_source_event`, V34)으로 동작하는지 검증한다. `NotificationService`를
 * Mock하는 다른 Notification Test와 달리, 이 Test는 실제 `NotificationServiceImpl`
 * +`NotificationInsertOperation`을 그대로 써서 UNIQUE 제약 위반 -> No-op 재사용 경로와, 여러
 * Thread가 실제로 동시에 같은 Row를 Insert 시도할 때의 결과까지 확인한다.
 */
@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = [
        "app.jwt.secret=notification-idempotency-integration-test-only-jwt-secret-value",
        "app.jwt.access-token-expiration-seconds=1800",
        "app.jwt.refresh-token-expiration-seconds=1209600",
        "app.file.storage.bucket=geti-integration-test",
        "app.file.storage.region=us-east-1",
        "app.file.storage.access-key=integration-test-only-access-key",
        "app.file.storage.secret-key=integration-test-only-secret-key",
    ],
)
class NotificationIdempotencyIntegrationTest {
    @Autowired
    private lateinit var notificationService: NotificationService

    @Autowired
    private lateinit var notificationRepository: NotificationRepository

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Test
    fun `같은 recipient와 sourceEvent로 두 번 생성해도 Row가 하나만 남는다`() {
        val memberId = createMember()
        val command = commandOf(memberId, sourceEventType = "InquiryAnsweredEvent", sourceEventId = 1L)

        val firstId = notificationService.create(command)
        val secondId = notificationService.create(command)

        assertThat(secondId).isEqualTo(firstId)
        assertThat(countBySourceEvent(memberId, "InquiryAnsweredEvent", 1L)).isEqualTo(1)
    }

    @Test
    fun `수신자가 다르면 같은 sourceEventType과 sourceEventId라도 각자 알림이 생긴다`() {
        val recipientA = createMember()
        val recipientB = createMember()

        val idA = notificationService.create(commandOf(recipientA, "ProgramDeletedEvent", 10L))
        val idB = notificationService.create(commandOf(recipientB, "ProgramDeletedEvent", 10L))

        assertThat(idA).isNotEqualTo(idB)
        assertThat(countBySourceEvent(recipientA, "ProgramDeletedEvent", 10L)).isEqualTo(1)
        assertThat(countBySourceEvent(recipientB, "ProgramDeletedEvent", 10L)).isEqualTo(1)
    }

    @Test
    fun `같은 recipient라도 sourceEventId가 다르면 서로 다른 상태 전이로 보고 별도 알림을 만든다`() {
        val memberId = createMember()

        // 같은 지원서가 REQUEST_REVISION 뒤 APPROVE된 것처럼, 같은 applicationId(target)라도
        // 검토 결과마다 historyId(sourceEventId)가 달라지는 상황을 재현한다.
        val firstReviewId =
            notificationService.create(commandOf(memberId, "JobApplicationReviewedEvent", 100L, targetId = 5L))
        val secondReviewId =
            notificationService.create(commandOf(memberId, "JobApplicationReviewedEvent", 101L, targetId = 5L))

        assertThat(firstReviewId).isNotEqualTo(secondReviewId)
        assertThat(countBySourceEvent(memberId, "JobApplicationReviewedEvent", 100L)).isEqualTo(1)
        assertThat(countBySourceEvent(memberId, "JobApplicationReviewedEvent", 101L)).isEqualTo(1)
    }

    @Test
    fun `알림을 삭제한 뒤 같은 원본 Event가 재처리돼도 다시 생기지 않는다`() {
        val memberId = createMember()
        val command = commandOf(memberId, "MemberApprovalProcessedEvent", 42L)
        val originalId = notificationService.create(command)
        notificationService.delete(memberId, originalId)
        assertThat(notificationRepository.findById(originalId).orElseThrow().deletedAt).isNotNull()

        val reprocessedId = notificationService.create(command)

        assertThat(reprocessedId).isEqualTo(originalId)
        assertThat(countBySourceEvent(memberId, "MemberApprovalProcessedEvent", 42L)).isEqualTo(1)
        // 재처리로 삭제 상태가 되살아나지 않는다 -- No-op은 기존 Row를 건드리지 않는다.
        assertThat(notificationRepository.findById(originalId).orElseThrow().deletedAt).isNotNull()
    }

    @Test
    fun `동시에 같은 원본 Event가 도착해도 DB 제약으로 Row가 하나만 남는다`() {
        val memberId = createMember()
        val command = commandOf(memberId, "InquiryAnsweredEvent", 777L)
        val readyToStart = CountDownLatch(2)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val task =
                Callable {
                    readyToStart.countDown()
                    start.await(AWAIT_SECONDS, TimeUnit.SECONDS)
                    notificationService.create(command)
                }
            val first = executor.submit(task)
            val second = executor.submit(task)

            // 두 Thread가 모두 대기 지점에 도달한 뒤에야 동시에 풀어준다 -- 우연한 순차 실행이
            // 아니라 실제 동시 시도임을 보장한다(InquiryAnswerConcurrencyIntegrationTest와 같은 이유,
            // 다만 여기서는 두 시도 모두 "이겨도 되는" 대칭 상황이라 Row Lock이 아니라 Insert
            // UNIQUE 제약이 승자를 가른다).
            assertThat(readyToStart.await(AWAIT_SECONDS, TimeUnit.SECONDS)).isTrue()
            start.countDown()

            val firstId = first.get(AWAIT_SECONDS, TimeUnit.SECONDS)
            val secondId = second.get(AWAIT_SECONDS, TimeUnit.SECONDS)

            // 둘 다 예외 없이 같은 id를 돌려받는다 -- 진 쪽은 UNIQUE 제약 위반을 잡아 이긴 쪽이
            // 이미 Commit한 Row를 재조회해서 반환한다(사전 조회가 아니라 실제 제약으로 수렴).
            assertThat(secondId).isEqualTo(firstId)
            assertThat(countBySourceEvent(memberId, "InquiryAnsweredEvent", 777L)).isEqualTo(1)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `과거 Row처럼 sourceEventType과 sourceEventId가 둘 다 NULL이면 서로 충돌하지 않는다`() {
        val memberId = createMember()

        // NotificationCreateCommand는 두 값을 필수로 요구하므로(Issue #193 신규 계약), 과거
        // Row(마이그레이션 이전에 만들어진 Row)는 Entity를 직접 만들어 재현한다.
        val legacyFirst = notificationRepository.saveAndFlush(legacyNotification(memberId, "이전 알림 1"))
        val legacySecond = notificationRepository.saveAndFlush(legacyNotification(memberId, "이전 알림 2"))

        assertThat(legacyFirst.id).isNotEqualTo(legacySecond.id)
        assertThat(legacyFirst.sourceEventType).isNull()
        assertThat(legacySecond.sourceEventType).isNull()
    }

    private fun legacyNotification(
        recipientMemberId: Long,
        title: String,
    ) = Notification(
        recipientMemberId = recipientMemberId,
        type = NotificationType.SYSTEM,
        title = title,
        content = "Migration 이전 알림",
    )

    private fun commandOf(
        recipientMemberId: Long,
        sourceEventType: String,
        sourceEventId: Long,
        targetId: Long? = null,
    ) = NotificationCreateCommand(
        recipientMemberId = recipientMemberId,
        type = NotificationType.SYSTEM,
        title = "제목",
        content = "내용",
        sourceEventType = sourceEventType,
        sourceEventId = sourceEventId,
        targetId = targetId,
    )

    private fun countBySourceEvent(
        recipientMemberId: Long,
        sourceEventType: String,
        sourceEventId: Long,
    ): Int =
        if (notificationRepository.findByRecipientMemberIdAndSourceEventTypeAndSourceEventId(
                recipientMemberId,
                sourceEventType,
                sourceEventId,
            ) != null
        ) {
            1
        } else {
            0
        }

    private fun createMember(): Long {
        val subject = UUID.randomUUID().toString()
        val member =
            memberRepository.saveAndFlush(
                Member(
                    oauthProvider = OAuthProvider.DG,
                    oauthSubject = subject,
                    email = "$subject@example.com",
                    status = MemberStatus.ACTIVE,
                ),
            )
        return requireNotNull(member.id)
    }

    companion object {
        private const val AWAIT_SECONDS = 10L

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
