package team.inreok.getiserver.domain.notification.scheduler

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.willThrow
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import team.inreok.getiserver.domain.notification.service.NotificationRetentionCleanupService
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
class NotificationRetentionSchedulerTest {
    @Mock
    private lateinit var notificationRetentionCleanupService: NotificationRetentionCleanupService

    private val scheduler: NotificationRetentionScheduler by lazy {
        NotificationRetentionScheduler(notificationRetentionCleanupService)
    }

    private fun anyDateTime(): LocalDateTime = any(LocalDateTime::class.java) ?: LocalDateTime.now()

    @Test
    fun `실행되면 정리 Service를 현재 시각으로 호출한다`() {
        given(notificationRetentionCleanupService.cleanupExpiredNotifications(anyDateTime())).willReturn(0)

        scheduler.cleanupExpiredNotifications()

        verify(notificationRetentionCleanupService).cleanupExpiredNotifications(anyDateTime())
    }

    @Test
    fun `정리 Service가 예외를 던져도 Scheduler 자체는 예외를 전파하지 않는다`() {
        willThrow(RuntimeException("Unexpected"))
            .given(notificationRetentionCleanupService)
            .cleanupExpiredNotifications(anyDateTime())

        scheduler.cleanupExpiredNotifications()
    }
}
