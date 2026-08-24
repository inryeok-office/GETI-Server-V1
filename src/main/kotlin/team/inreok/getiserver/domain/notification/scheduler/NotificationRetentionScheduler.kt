package team.inreok.getiserver.domain.notification.scheduler

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import team.inreok.getiserver.domain.notification.service.NotificationRetentionCleanupService
import java.time.LocalDateTime

/**
 * 보관 기한이 지난 알림을 주기적으로 Hard Delete한다(Issue #194).
 *
 * `ProgramCloseScheduler`/`DiscordDeliveryWorker`와 같은 구조다 -- 이 Class는 "언제 도는가"만
 * 알고, "무엇을 지우는가"(Batch 조회 + 삭제)는 [NotificationRetentionCleanupService]가 안다.
 *
 * 한 정책(예: Soft Delete 대상 삭제)이 예외를 던져도 나머지 정책 처리를 막지 않도록
 * `runCatching`으로 감싼다(`SearchIndexRetryScheduler.retryDueFailures`와 동일한 방식) -- 실제
 * 정책별 격리는 `NotificationRetentionCleanupService` 구현이 각 정책을 독립적으로 처리하고 예외를
 * 던지지 않는 정상 흐름에서 이뤄지지만, 예상하지 못한 오류가 Scheduler 자체까지 전파돼 다음 주기
 * 등록이 깨지는 것을 막기 위한 최종 방어선이다.
 */
@Component
class NotificationRetentionScheduler(
    private val notificationRetentionCleanupService: NotificationRetentionCleanupService,
) {
    private val log = LoggerFactory.getLogger(NotificationRetentionScheduler::class.java)

    @Scheduled(fixedDelayString = "\${app.notification.retention.scheduler-interval-ms:3600000}")
    fun cleanupExpiredNotifications() {
        runCatching { notificationRetentionCleanupService.cleanupExpiredNotifications(LocalDateTime.now()) }
            .onFailure { ex -> log.error("알림 보관 기한 정리 중 처리되지 않은 오류", ex) }
    }
}
