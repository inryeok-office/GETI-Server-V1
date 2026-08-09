package team.inreok.getiserver.domain.notification.scheduler

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import team.inreok.getiserver.domain.notification.service.DiscordDeliveryService

/**
 * 처리할 때가 된 Discord 전달을 주기적으로 훑는다(후속 요구사항 문서 §30).
 *
 * Scheduler와 처리 로직을 분리하는 것은 `JobNotificationRetryScheduler` →
 * `JobNotificationService`와 같은 구조다 -- 이 Class는 "언제 도는가"만 알고, "무엇을 하는가"는
 * Service가 안다.
 *
 * 기본 60초 주기다. 자동 재시도의 최소 백오프가 1분이라(§17) 그보다 촘촘하게 돌 이유가 없고,
 * Collector의 5분 주기를 그대로 쓰면 1분 백오프가 사실상 5분으로 늘어난다.
 *
 * Discord Bot 설정이 갖춰지지 않았으면 `processDueDeliveries()`가 즉시 반환한다 -- 그동안
 * Delivery는 PENDING으로 쌓여 있다가 설정이 채워지면 재배포 없이 이어서 처리된다.
 */
@Component
class DiscordDeliveryWorker(
    private val discordDeliveryService: DiscordDeliveryService,
) {
    @Scheduled(fixedDelayString = "\${app.discord.bot.sweep-interval-ms:60000}")
    fun sweep() {
        discordDeliveryService.processDueDeliveries()
    }
}
