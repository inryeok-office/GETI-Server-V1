package team.inreok.getiserver.domain.notification.service

import team.inreok.getiserver.domain.notification.dto.DiscordDeliveryEnqueueCommand

/**
 * Discord 전달의 생성·처리·재시도 계약이다.
 *
 * **REST로 노출되지 않는다.** 후속 요구사항 문서 §36·§37의 상태 조회·수동 재시도 API는
 * Job/Program/Inquiry Domain Event가 붙어 실제로 Delivery Row가 생기는 후속 PR에서 추가한다 --
 * 그 전에 Endpoint를 만들면 운영에서 영원히 빈 결과만 반환하고 재시도할 Row도 없어 검증이
 * 불가능하다.
 */
interface DiscordDeliveryService {
    /**
     * Discord 전달을 예약한다. 이미 같은 논리적 작업이 예약돼 있으면 새로 만들지 않고 기존
     * Delivery의 id를 돌려준다(§49) -- Application Event는 exactly-once가 아니라 같은 Event가
     * 두 번 도착할 수 있기 때문이다.
     *
     * 원본 Transaction이 Commit된 뒤에 호출해야 한다(§29). 이 메서드는 Bot을 호출하지 않고 Row만
     * 만들므로, Discord 장애가 원본 비즈니스 Transaction을 되돌리지 않는다(§48).
     */
    fun enqueue(command: DiscordDeliveryEnqueueCommand): Long

    /**
     * 처리할 때가 된 Delivery를 훑어 Bot으로 전송한다. Scheduler가 주기적으로 호출한다.
     * 설정이 갖춰지지 않았으면 아무것도 하지 않는다.
     *
     * @return 이번 회차에 실제로 처리한 건수.
     */
    fun processDueDeliveries(): Int

    /**
     * 사람이 요청한 수동 재시도다(§18). FAILED 상태이고 수동 상한이 남아 있을 때만 허용하며,
     * 새 Delivery를 만들지 않고 같은 Row를 재사용한다.
     *
     * 실제 전송은 다음 Sweep이 수행한다 -- 여기서 바로 Bot을 호출하면 요청 Thread가 수 초짜리
     * HTTP를 기다리게 되고, Worker와 같은 Row를 동시에 건드릴 위험도 생긴다.
     */
    fun retryManually(deliveryId: Long)
}
