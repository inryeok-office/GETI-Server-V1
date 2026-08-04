package team.inreok.getiserver.domain.collector.notification.scheduler

import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.dao.DataAccessException
import org.springframework.stereotype.Component
import team.inreok.getiserver.domain.collector.entity.type.JobNotificationDeliveryStatus
import team.inreok.getiserver.domain.collector.repository.JobNotificationDeliveryRepository

/**
 * 서버가 SENDING 상태(Discord 발송 시도 중) Delivery를 갱신하기 전에 재시작되면 그 Row가
 * 영구적으로 "발송 중"처럼 남아 재시도 대상( PENDING/FAILED )에서 빠진다.
 * [CollectorStaleRunRecoveryRunner]와 같은 이유·같은 방어(DataAccessException 시 기동 자체는
 * 막지 않음)로, 기동 시 한 번 SENDING Row를 PENDING으로 되돌려 다음 재시도 Sweep이 다시 집어가게
 * 한다.
 */
@Component
class JobNotificationStaleDeliveryRecoveryRunner(
    private val deliveryRepository: JobNotificationDeliveryRepository,
) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        val stale =
            try {
                deliveryRepository.findAllByStatus(JobNotificationDeliveryStatus.SENDING)
            } catch (ex: DataAccessException) {
                log.warn("서버 재시작 정리용 알림 Delivery 조회에 실패해 이번 기동에서는 건너뜁니다.", ex)
                return
            }
        if (stale.isEmpty()) return

        stale.forEach { delivery ->
            delivery.status = JobNotificationDeliveryStatus.PENDING
            delivery.nextRetryAt = null
        }
        deliveryRepository.saveAll(stale)
        log.warn("서버 재시작으로 중단된 Discord 알림 발송 {}건을 재시도 대기로 되돌렸습니다.", stale.size)
    }

    private companion object {
        val log = LoggerFactory.getLogger(JobNotificationStaleDeliveryRecoveryRunner::class.java)
    }
}
