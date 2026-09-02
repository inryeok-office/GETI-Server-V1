package team.inreok.getiserver.domain.notification.scheduler

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import team.inreok.getiserver.domain.notification.service.PushDeliveryService

/**
 * 처리할 때가 된 Push 전달을 주기적으로 훑는다(`DiscordDeliveryWorker`와 같은 구조 -- Scheduler와
 * 처리 로직을 분리해 이 Class는 "언제 도는가"만 알고 "무엇을 하는가"는 Service가 안다).
 *
 * FCM이 설정되지 않았으면 `PushDeliveryServiceImpl.send`가 매번 `TOKEN_UNAVAILABLE`/
 * `NOT_CONFIGURED` 실패로 처리하고 재시도 가능으로 분류해, 설정이 채워지면 재배포 없이 이어서
 * 처리된다(`DiscordDeliveryWorker`와 같은 방식).
 */
@Component
class PushDeliveryWorker(
    private val pushDeliveryService: PushDeliveryService,
) {
    @Scheduled(fixedDelayString = "\${app.push.delivery.sweep-interval-ms:15000}")
    fun sweep() {
        pushDeliveryService.processDueDeliveries()
    }
}
