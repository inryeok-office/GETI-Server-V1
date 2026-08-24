package team.inreok.getiserver.domain.notification

import com.redis.testcontainers.RedisContainer
import jakarta.persistence.EntityManager
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
import team.inreok.getiserver.domain.member.entity.Member
import team.inreok.getiserver.domain.member.entity.type.OAuthProvider
import team.inreok.getiserver.domain.member.repository.MemberRepository
import team.inreok.getiserver.domain.notification.entity.Notification
import team.inreok.getiserver.domain.notification.entity.type.NotificationType
import team.inreok.getiserver.domain.notification.repository.NotificationRepository
import team.inreok.getiserver.domain.notification.service.NotificationRetentionCleanupService
import java.time.LocalDateTime
import java.util.UUID

/**
 * 인앱 알림 보관·정리 정책이 실제 PostgreSQL에서 30/180/365일 경계와 세 정책의 상호 배타성을
 * 지키는지 검증한다(Issue #194). Default 설정값(`application.yaml`의
 * `app.notification.retention.*` 기본값)을 그대로 쓰고, `now`는 [NotificationRetentionCleanupService]에
 * 직접 주입해 경계를 정확히 통제한다(`ProgramCloseSchedulerIntegrationTest`의
 * `programCloseService.closeIfExpired(programId, now)` 직접 호출과 같은 방식).
 */
@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = [
        "app.jwt.secret=notification-retention-cleanup-integration-test-only-jwt-secret-value",
        "app.jwt.access-token-expiration-seconds=1800",
        "app.jwt.refresh-token-expiration-seconds=1209600",
        "app.file.storage.bucket=geti-integration-test",
        "app.file.storage.region=us-east-1",
        "app.file.storage.access-key=integration-test-only-access-key",
        "app.file.storage.secret-key=integration-test-only-secret-key",
    ],
)
class NotificationRetentionCleanupIntegrationTest {
    @Autowired
    private lateinit var notificationRetentionCleanupService: NotificationRetentionCleanupService

    @Autowired
    private lateinit var notificationRepository: NotificationRepository

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Autowired
    private lateinit var entityManager: EntityManager

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    private val now = LocalDateTime.of(2026, 8, 23, 12, 0)

    @Test
    fun `Soft Delete 29일째는 살아남고 30일째와 30일+1초는 정리된다`() {
        val memberId = createMember()
        val survives = persist(memberId, deletedAt = now.minusDays(29))
        val exactlyExpired = persist(memberId, deletedAt = now.minusDays(30))
        val pastExpired = persist(memberId, deletedAt = now.minusDays(30).minusSeconds(1))

        notificationRetentionCleanupService.cleanupExpiredNotifications(now)

        assertThat(notificationRepository.existsById(survives)).isTrue()
        assertThat(notificationRepository.existsById(exactlyExpired)).isFalse()
        assertThat(notificationRepository.existsById(pastExpired)).isFalse()
    }

    @Test
    fun `읽은 알림은 179일째 살아남고 180일째와 181일째는 정리된다`() {
        val memberId = createMember()
        val survives = persist(memberId, isRead = true, readAt = now.minusDays(179))
        val exactlyExpired = persist(memberId, isRead = true, readAt = now.minusDays(180))
        val pastExpired = persist(memberId, isRead = true, readAt = now.minusDays(181))

        notificationRetentionCleanupService.cleanupExpiredNotifications(now)

        assertThat(notificationRepository.existsById(survives)).isTrue()
        assertThat(notificationRepository.existsById(exactlyExpired)).isFalse()
        assertThat(notificationRepository.existsById(pastExpired)).isFalse()
    }

    @Test
    fun `읽지 않은 알림은 364일째 살아남고 365일째와 366일째는 정리된다`() {
        val memberId = createMember()
        val survives = persist(memberId, isRead = false, createdAt = now.minusDays(364))
        val exactlyExpired = persist(memberId, isRead = false, createdAt = now.minusDays(365))
        val pastExpired = persist(memberId, isRead = false, createdAt = now.minusDays(366))

        notificationRetentionCleanupService.cleanupExpiredNotifications(now)

        assertThat(notificationRepository.existsById(survives)).isTrue()
        assertThat(notificationRepository.existsById(exactlyExpired)).isFalse()
        assertThat(notificationRepository.existsById(pastExpired)).isFalse()
    }

    @Test
    fun `Soft Delete된 알림은 아직 30일이 안 지났으면 읽음-읽지 않음 정책으로도 지워지지 않는다`() {
        val memberId = createMember()
        // 아직 Soft Delete 유예 기간(30일) 안이라 살아남아야 한다. 동시에 readAt/createdAt은
        // 각각 읽음(180일)/읽지 않음(365일) 정책만 놓고 보면 이미 만료된 값이다 -- Soft Delete
        // 정책과 읽음/읽지 않음 정책이 같은 Row를 이중으로 처리하지 않는지가 이 Test의 핵심이다.
        val recentlyDeletedButOldRead =
            persist(memberId, isRead = true, readAt = now.minusDays(400), deletedAt = now.minusDays(5))
        val recentlyDeletedButOldCreated =
            persist(memberId, isRead = false, createdAt = now.minusDays(400), deletedAt = now.minusDays(5))

        notificationRetentionCleanupService.cleanupExpiredNotifications(now)

        assertThat(notificationRepository.existsById(recentlyDeletedButOldRead)).isTrue()
        assertThat(notificationRepository.existsById(recentlyDeletedButOldCreated)).isTrue()
    }

    @Test
    fun `다른 회원과 최근 생성된 알림은 대상에서 제외된다`() {
        val ownerId = createMember()
        val otherId = createMember()
        val recentUnread = persist(ownerId, isRead = false, createdAt = now)
        val otherExpiredSoftDeleted = persist(otherId, deletedAt = now.minusDays(31))

        notificationRetentionCleanupService.cleanupExpiredNotifications(now)

        assertThat(notificationRepository.existsById(recentUnread)).isTrue()
        // 다른 회원 소유라고 봐줄 조건은 없다 -- 이 정책은 전역 정리이므로 만료됐으면 지워진다.
        assertThat(notificationRepository.existsById(otherExpiredSoftDeleted)).isFalse()
    }

    @Test
    fun `실제로 지운 총 건수를 반환한다`() {
        val memberId = createMember()
        persist(memberId, deletedAt = now.minusDays(31))
        persist(memberId, isRead = true, readAt = now.minusDays(181))
        persist(memberId, isRead = false, createdAt = now.minusDays(366))
        persist(memberId, isRead = false, createdAt = now)

        val deletedCount = notificationRetentionCleanupService.cleanupExpiredNotifications(now)

        assertThat(deletedCount).isEqualTo(3)
    }

    private fun persist(
        recipientMemberId: Long,
        isRead: Boolean = false,
        readAt: LocalDateTime? = null,
        createdAt: LocalDateTime? = null,
        deletedAt: LocalDateTime? = null,
    ): Long {
        val notification =
            notificationRepository.saveAndFlush(
                Notification(
                    recipientMemberId = recipientMemberId,
                    type = NotificationType.JOB_PUBLISHED,
                    title = "제목",
                    content = "내용",
                    isRead = isRead,
                ).apply {
                    this.readAt = readAt
                    this.deletedAt = deletedAt
                },
            )
        val notificationId = requireNotNull(notification.id)
        // created_at은 @CreationTimestamp(INSERT 시점 생성) + updatable=false라 Entity를 다시
        // save해도 JPA가 그 Column을 UPDATE에 포함하지 않는다 -- 경계 Test를 위해 임의의 과거
        // 시각을 강제해야 하므로 Native Query로 직접 갱신한다(CollectorRepositoryIntegrationTest와
        // 동일한 방식). `@SpringBootTest`는 `@DataJpaTest`와 달리 Test Method를 Transaction으로
        // 감싸지 않아 `executeUpdate()`가 TransactionRequiredException을 던지므로,
        // `TransactionTemplate`로 짧은 Transaction을 직접 연다
        // (JobApplicationNotificationIntegrationTest와 동일한 방식).
        if (createdAt != null) {
            TransactionTemplate(transactionManager).executeWithoutResult {
                entityManager
                    .createNativeQuery("UPDATE notifications SET created_at = :createdAt WHERE id = :id")
                    .setParameter("createdAt", createdAt)
                    .setParameter("id", notificationId)
                    .executeUpdate()
            }
            entityManager.clear()
        }
        return notificationId
    }

    private fun createMember(): Long {
        val subject = UUID.randomUUID().toString()
        val member =
            memberRepository.saveAndFlush(
                Member(oauthProvider = OAuthProvider.DG, oauthSubject = subject, email = "$subject@example.com"),
            )
        return requireNotNull(member.id)
    }

    companion object {
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
