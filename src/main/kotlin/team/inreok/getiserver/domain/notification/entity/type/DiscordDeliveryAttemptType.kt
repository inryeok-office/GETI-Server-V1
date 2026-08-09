package team.inreok.getiserver.domain.notification.entity.type

/**
 * 한 번의 Bot 호출이 자동 재시도에서 비롯됐는지 사람이 누른 수동 재시도에서 비롯됐는지를
 * 구분한다(후속 요구사항 문서 §12).
 *
 * 수동 재시도는 `automaticRetryCount`를 0으로 되돌리므로 Delivery Row의 카운터만으로는 과거
 * 자동 시도 이력을 알 수 없다. 시도 이력 전체는 `discord_delivery_attempts`에 남고, 이 값이
 * 각 시도의 출처를 알려준다.
 */
enum class DiscordDeliveryAttemptType {
    AUTOMATIC,
    MANUAL,
}
