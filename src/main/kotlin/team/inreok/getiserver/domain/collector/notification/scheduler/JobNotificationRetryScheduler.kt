package team.inreok.getiserver.domain.collector.notification.scheduler

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import team.inreok.getiserver.domain.collector.notification.service.JobNotificationService

/**
 * 실패했거나 아직 시도하지 않은 Discord 알림 Delivery를 주기적으로 재시도한다. 재시작 이후에도
 * DB에 남은 PENDING/FAILED Row를 그대로 다시 집어 처리하므로 별도 복구 로직 없이 재시도가
 * 이어진다(Issue #62 확장 범위). Discord 알림이 비활성/미설정이면
 * `JobNotificationService.processDueRetries()`가 즉시 반환한다.
 */
@Component
class JobNotificationRetryScheduler(
    private val jobNotificationService: JobNotificationService,
) {
    @Scheduled(fixedDelayString = "\${app.discord.job-notification.retry-interval-ms:300000}")
    fun retryDueDeliveries() {
        jobNotificationService.processDueRetries()
    }
}
