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
 * `app.notification.retention.*`를 기본값과 다르게 재정의했을 때 실제로 그 값이 적용되는지, 그리고
 * Batch 상한이 한 번의 실행에서 지켜지고 나머지는 다음 실행에서 정리되는지 검증한다(Issue #194).
 *
 * 보관 일수를 짧게(5/10/20일) 재정의해, "기본값(30/180/365일)이었다면 살아남았을" 알림이 이
 * Context에서는 정리되는 것을 확인한다 -- Default 설정 그대로였다면 이 차이를 볼 수 없다.
 */
@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = [
        "app.jwt.secret=notification-retention-custom-config-integration-test-only-jwt-secret-value",
        "app.jwt.access-token-expiration-seconds=1800",
        "app.jwt.refresh-token-expiration-seconds=1209600",
        "app.file.storage.bucket=geti-integration-test",
        "app.file.storage.region=us-east-1",
        "app.file.storage.access-key=integration-test-only-access-key",
        "app.file.storage.secret-key=integration-test-only-secret-key",
        "app.notification.retention.soft-deleted-retention-days=5",
        "app.notification.retention.read-retention-days=10",
        "app.notification.retention.unread-retention-days=20",
        "app.notification.retention.batch-size=2",
    ],
)
class NotificationRetentionCleanupCustomConfigIntegrationTest {
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
    fun `재정의한 보관 일수가 실제로 적용된다`() {
        val memberId = createMember()
        // 기본값(30/180/365일)이었다면 셋 다 살아남았을 나이지만, 재정의한 값(5/10/20일)으로는
        // 이미 만료됐다.
        val softDeleted = persist(memberId, deletedAt = now.minusDays(10))
        val read = persist(memberId, isRead = true, readAt = now.minusDays(15))
        val unread = persist(memberId, isRead = false, createdAt = now.minusDays(25))

        notificationRetentionCleanupService.cleanupExpiredNotifications(now)

        assertThat(notificationRepository.existsById(softDeleted)).isFalse()
        assertThat(notificationRepository.existsById(read)).isFalse()
        assertThat(notificationRepository.existsById(unread)).isFalse()
    }

    @Test
    fun `Batch 상한(2건)이 한 번의 실행에서 지켜지고, 남은 대상은 다음 실행에서 정리된다`() {
        val memberId = createMember()
        val expiredIds = (1..5).map { persist(memberId, isRead = false, createdAt = now.minusDays(25)) }

        val firstRunDeleted = notificationRetentionCleanupService.cleanupExpiredNotifications(now)
        assertThat(firstRunDeleted).isEqualTo(2)
        assertThat(expiredIds.count { notificationRepository.existsById(it) }).isEqualTo(3)

        val secondRunDeleted = notificationRetentionCleanupService.cleanupExpiredNotifications(now)
        assertThat(secondRunDeleted).isEqualTo(2)
        assertThat(expiredIds.count { notificationRepository.existsById(it) }).isEqualTo(1)

        val thirdRunDeleted = notificationRetentionCleanupService.cleanupExpiredNotifications(now)
        assertThat(thirdRunDeleted).isEqualTo(1)
        assertThat(expiredIds.none { notificationRepository.existsById(it) }).isTrue()
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
