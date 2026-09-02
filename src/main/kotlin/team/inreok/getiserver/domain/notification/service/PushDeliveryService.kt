package team.inreok.getiserver.domain.notification.service

import team.inreok.getiserver.domain.notification.entity.type.NotificationType

interface PushDeliveryService {
    /**
     * 방금 생성된 인앱 알림 한 건을 대상 회원의 등록된 기기 모두에 Push로 보낼지 판단하고, 보낼
     * 것이면 기기별 `PushDelivery`를 PENDING으로 예약한다(Issue #190).
     *
     * 다음 중 하나라도 해당하면 아무것도 만들지 않고 조용히 반환한다(예외를 던지지 않는다 --
     * 알림 자체의 생성은 이미 끝났고, Push는 부가 기능이다).
     * - [type]이 [PushEligibleNotificationTypes]에 없다.
     * - 대상 회원의 `NotificationSetting.pushEnabled`가 false다.
     * - 대상 회원이 등록한 기기가 없다.
     *
     * **항상 새 Transaction에서 독립적으로 Commit된다(`REQUIRES_NEW`).** `NotificationService.create`
     * 는 `@TransactionalEventListener(AFTER_COMMIT)` Listener가 호출하는 것을 전제로 하며, 그
     * 시점에는 원본 Transaction이 이미 Commit됐지만 Thread에 아직 바인딩된 상태다. 기본
     * `REQUIRED`로 두면 이 예약이 그 끝난 Transaction에 참여해 예외 없이 조용히 사라진다
     * (`NotificationInsertOperation`과 같은 이유, Issue #118).
     *
     * 실제 Provider 호출(네트워크 I/O)은 여기서 하지 않는다 -- Row만 PENDING으로 남기고, 실제
     * 전송은 [processDueDeliveries]가 별도 Transaction 밖에서 수행한다.
     */
    fun enqueueForNotification(
        notificationId: Long,
        recipientMemberId: Long,
        type: NotificationType,
    )

    /**
     * 처리할 때가 된 Push 전달을 훑어 Provider로 전송한다(`DiscordDeliveryService
     * .processDueDeliveries`와 같은 구조). `@Transactional`이 없다 -- Provider HTTP 호출을
     * Transaction 안에 넣지 않기 위해서다(.claude/rules/spring-boot.md).
     *
     * @return 이번 Sweep에서 실제로 처리(전송 시도)한 건수.
     */
    fun processDueDeliveries(): Int
}
