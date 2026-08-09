package team.inreok.getiserver.domain.notification.entity.type

/**
 * Discord 전달의 공통 상태다(후속 요구사항 문서 §7). `domain.notification`이 이 계약의
 * Source of Truth이며, 다른 도메인이 각자 정의한 Discord 상태(`domain.program`의
 * `DiscordDeliveryStatus(SUCCESS/FAILED/SKIPPED)`, `domain.inquiry`의
 * `InquiryDiscordDeliveryStatus(PENDING)`)는 후속 PR에서 이 값 집합으로 교체한다.
 *
 * `domain.collector`의 `JobNotificationDeliveryStatus(PENDING/SENDING/SENT/FAILED)`는 이름이
 * 비슷하지만 **완전히 별개의 Subsystem**(수집 공고용 Discord Webhook)이다. 이 Package에서
 * 그 Enum을 import하면 안 된다(후속 요구사항 문서 §35).
 */
enum class DiscordDeliveryStatus {
    /** Delivery가 생성됐고 아직 Worker가 집어가지 않았다. 자동 재시도 대기 상태도 여기에 포함된다. */
    PENDING,

    /** Worker가 선점해 현재 Bot으로 전송 처리 중이다. */
    PROCESSING,

    /** Bot이 Discord 작업을 성공적으로 완료했다. */
    DELIVERED,

    /** 허용된 자동 재시도를 모두 소진했거나, 재시도해도 달라지지 않는 오류를 만났다. */
    FAILED,
}
