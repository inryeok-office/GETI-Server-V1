package team.inreok.getiserver.domain.notification

import com.redis.testcontainers.RedisContainer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.data.domain.PageRequest
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import team.inreok.getiserver.domain.member.entity.Member
import team.inreok.getiserver.domain.member.entity.type.MemberStatus
import team.inreok.getiserver.domain.member.entity.type.OAuthProvider
import team.inreok.getiserver.domain.member.repository.MemberRepository
import team.inreok.getiserver.domain.notification.dto.NotificationCreateCommand
import team.inreok.getiserver.domain.notification.dto.NotificationReadRequest
import team.inreok.getiserver.domain.notification.dto.NotificationReadScope
import team.inreok.getiserver.domain.notification.entity.type.NotificationType
import team.inreok.getiserver.domain.notification.exception.NotificationAccessDeniedException
import team.inreok.getiserver.domain.notification.exception.NotificationNotFoundException
import team.inreok.getiserver.domain.notification.repository.NotificationRepository
import team.inreok.getiserver.domain.notification.service.NotificationService
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/**
 * 인앱 알림 Inbox의 조회·읽음·삭제가 실제 PostgreSQL에서 의도대로 동작하는지 검증한다(Issue #187).
 *
 * Service Test(Mock)로는 확인할 수 없는 것만 여기서 본다.
 * - Soft Delete(`deleted_at`)가 목록·읽지 않은 개수·읽음 처리 세 경로 모두에서 실제로 제외되는지
 *   (세 Query에 각각 `deletedAt IS NULL`이 걸려 있어야 하며, 한 곳만 빠져도 Mock Test는 통과한다)
 * - `markAllAsRead` Bulk JPQL이 다른 사용자와 삭제된 Row를 건드리지 않는지
 * - `createdAt`이 같을 때 `id DESC` Tie-breaker가 실제 정렬에 적용되는지
 */
@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = [
        "app.jwt.secret=notification-inbox-persistence-integration-test-only-jwt-secret-value",
        "app.jwt.access-token-expiration-seconds=1800",
        "app.jwt.refresh-token-expiration-seconds=1209600",
        "app.file.storage.bucket=geti-integration-test",
        "app.file.storage.region=us-east-1",
        "app.file.storage.access-key=integration-test-only-access-key",
        "app.file.storage.secret-key=integration-test-only-secret-key",
    ],
)
class NotificationInboxPersistenceIntegrationTest {
    @Autowired
    private lateinit var notificationService: NotificationService

    @Autowired
    private lateinit var notificationRepository: NotificationRepository

    @Autowired
    private lateinit var memberRepository: MemberRepository

    private var ownerId: Long = 0
    private var otherId: Long = 0

    @BeforeEach
    fun setUp() {
        ownerId = createMember()
        otherId = createMember()
    }

    @Test
    fun `내 알림만 조회되고 다른 사용자의 알림은 보이지 않는다`() {
        create(ownerId, NotificationType.JOB_PUBLISHED)
        create(otherId, NotificationType.JOB_PUBLISHED)

        val response = notificationService.list(ownerId, unreadOnly = false, notificationType = null, pageable = page())

        assertThat(response.totalElements).isEqualTo(1L)
        assertThat(response.unreadCount).isEqualTo(1L)
    }

    @Test
    fun `최신순으로 정렬하고 생성 시각이 같으면 id가 큰 알림을 먼저 내려준다`() {
        // 같은 Transaction 밖에서 연속 생성해도 createdAt이 밀리초까지 같을 수 있어 Tie-breaker가 필요하다.
        val first = create(ownerId, NotificationType.JOB_PUBLISHED)
        val second = create(ownerId, NotificationType.JOB_PUBLISHED)
        val third = create(ownerId, NotificationType.JOB_PUBLISHED)

        val content =
            notificationService
                .list(ownerId, unreadOnly = false, notificationType = null, pageable = page())
                .content

        assertThat(content.map { it.notificationId }).containsExactly(third, second, first)
    }

    @Test
    fun `unreadOnly와 notificationType 필터를 함께 적용한다`() {
        val unreadJob = create(ownerId, NotificationType.JOB_PUBLISHED)
        create(ownerId, NotificationType.PROGRAM_PUBLISHED)
        val readJob = create(ownerId, NotificationType.JOB_PUBLISHED)
        notificationService.read(ownerId, NotificationReadRequest(NotificationReadScope.SINGLE, readJob))

        val content =
            notificationService
                .list(
                    ownerId,
                    unreadOnly = true,
                    notificationType = NotificationType.JOB_PUBLISHED,
                    pageable = page(),
                ).content

        assertThat(content.map { it.notificationId }).containsExactly(unreadJob)
    }

    @Test
    fun `필터를 걸어도 unreadCount는 전체 미읽음 수를 유지한다`() {
        create(ownerId, NotificationType.JOB_PUBLISHED)
        create(ownerId, NotificationType.PROGRAM_PUBLISHED)
        create(ownerId, NotificationType.INQUIRY_ANSWERED)

        val response =
            notificationService.list(
                ownerId,
                unreadOnly = true,
                notificationType = NotificationType.JOB_PUBLISHED,
                pageable = page(),
            )

        assertThat(response.totalElements).isEqualTo(1L)
        assertThat(response.unreadCount).isEqualTo(3L)
    }

    @Test
    fun `삭제한 알림은 목록과 읽지 않은 개수에서 모두 빠진다`() {
        val kept = create(ownerId, NotificationType.JOB_PUBLISHED)
        val removed = create(ownerId, NotificationType.JOB_PUBLISHED)

        notificationService.delete(ownerId, removed)

        val response = notificationService.list(ownerId, unreadOnly = false, notificationType = null, pageable = page())
        assertThat(response.content.map { it.notificationId }).containsExactly(kept)
        assertThat(response.unreadCount).isEqualTo(1L)
        assertThat(notificationService.countUnread(ownerId).unreadCount).isEqualTo(1L)

        // Row 자체는 남아 있고 deleted_at만 채워진다(Soft Delete).
        assertThat(notificationRepository.findById(removed).orElseThrow().deletedAt).isNotNull()
    }

    @Test
    fun `삭제한 알림은 읽음 처리 대상에서도 빠진다`() {
        val removed = create(ownerId, NotificationType.JOB_PUBLISHED)
        notificationService.delete(ownerId, removed)

        assertThatThrownBy {
            notificationService.read(ownerId, NotificationReadRequest(NotificationReadScope.SINGLE, removed))
        }.isInstanceOf(NotificationNotFoundException::class.java)

        // 전체 읽음 Bulk UPDATE도 건드리지 않아 여전히 읽지 않은 상태로 남는다.
        val response = notificationService.read(ownerId, NotificationReadRequest(NotificationReadScope.ALL))
        assertThat(response.updatedCount).isZero()
        assertThat(notificationRepository.findById(removed).orElseThrow().isRead).isFalse()
    }

    @Test
    fun `이미 삭제한 알림을 다시 삭제하면 404다`() {
        val removed = create(ownerId, NotificationType.JOB_PUBLISHED)
        notificationService.delete(ownerId, removed)

        assertThatThrownBy { notificationService.delete(ownerId, removed) }
            .isInstanceOf(NotificationNotFoundException::class.java)
    }

    @Test
    fun `다른 사용자의 알림은 읽음 처리하거나 삭제할 수 없다`() {
        val foreign = create(otherId, NotificationType.JOB_PUBLISHED)

        assertThatThrownBy {
            notificationService.read(ownerId, NotificationReadRequest(NotificationReadScope.SINGLE, foreign))
        }.isInstanceOf(NotificationAccessDeniedException::class.java)
        assertThatThrownBy { notificationService.delete(ownerId, foreign) }
            .isInstanceOf(NotificationAccessDeniedException::class.java)

        val untouched = notificationRepository.findById(foreign).orElseThrow()
        assertThat(untouched.isRead).isFalse()
        assertThat(untouched.deletedAt).isNull()
    }

    @Test
    fun `전체 읽음은 내 읽지 않은 알림만 한 번에 처리하고 다른 사용자에게 영향을 주지 않는다`() {
        val alreadyRead = create(ownerId, NotificationType.JOB_PUBLISHED)
        notificationService.read(ownerId, NotificationReadRequest(NotificationReadScope.SINGLE, alreadyRead))
        val firstReadAt = notificationRepository.findById(alreadyRead).orElseThrow().readAt
        create(ownerId, NotificationType.JOB_PUBLISHED)
        create(ownerId, NotificationType.PROGRAM_PUBLISHED)
        val foreign = create(otherId, NotificationType.JOB_PUBLISHED)

        val response = notificationService.read(ownerId, NotificationReadRequest(NotificationReadScope.ALL))

        assertThat(response.updatedCount).isEqualTo(2L)
        assertThat(response.unreadCount).isZero()
        assertThat(response.readAt).isNotNull()
        // 이미 읽은 알림의 readAt은 덮어쓰지 않는다.
        assertThat(notificationRepository.findById(alreadyRead).orElseThrow().readAt).isEqualTo(firstReadAt)
        assertThat(notificationRepository.findById(foreign).orElseThrow().isRead).isFalse()
        assertThat(notificationService.countUnread(otherId).unreadCount).isEqualTo(1L)
    }

    @Test
    fun `Pagination이 실제 Query에 적용된다`() {
        repeat(3) { create(ownerId, NotificationType.JOB_PUBLISHED) }

        val firstPage =
            notificationService.list(ownerId, unreadOnly = false, notificationType = null, pageable = page(0, 2))
        val secondPage =
            notificationService.list(ownerId, unreadOnly = false, notificationType = null, pageable = page(1, 2))

        assertThat(firstPage.content).hasSize(2)
        assertThat(firstPage.totalElements).isEqualTo(3L)
        assertThat(firstPage.totalPages).isEqualTo(2)
        assertThat(firstPage.first).isTrue()
        assertThat(firstPage.last).isFalse()
        assertThat(secondPage.content).hasSize(1)
        assertThat(secondPage.last).isTrue()
    }

    private fun page(
        pageNumber: Int = 0,
        pageSize: Int = PAGE_SIZE,
    ) = PageRequest.of(pageNumber, pageSize)

    // 이 Test는 같은 recipientMemberId로 알림을 여러 건 만드는 경우가 많다(정렬·Pagination 검증
    // 등). Idempotency UNIQUE 제약(recipientMemberId + sourceEventType + sourceEventId)에 걸리지
    // 않도록 호출마다 다른 sourceEventId를 준다 -- 이 Test의 관심사는 Idempotency 자체가 아니라
    // 조회·읽음·삭제이므로 "서로 다른 알림"을 표현하기만 하면 된다.
    private val sourceEventIdSequence = AtomicLong()

    private fun create(
        recipientMemberId: Long,
        type: NotificationType,
    ): Long =
        notificationService.create(
            NotificationCreateCommand(
                recipientMemberId = recipientMemberId,
                type = type,
                title = "제목",
                content = "내용",
                sourceEventType = "NotificationInboxPersistenceIntegrationTest",
                sourceEventId = sourceEventIdSequence.incrementAndGet(),
            ),
        )

    private fun createMember(): Long {
        val subject = UUID.randomUUID().toString()
        val member =
            memberRepository.saveAndFlush(
                Member(
                    oauthProvider = OAuthProvider.GOOGLE,
                    oauthSubject = subject,
                    email = "$subject@example.com",
                    status = MemberStatus.ACTIVE,
                ).apply { name = subject },
            )
        return requireNotNull(member.id)
    }

    companion object {
        private const val PAGE_SIZE = 20

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
