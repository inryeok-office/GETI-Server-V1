package team.inreok.getiserver.domain.notification.entity.type

/**
 * 한 번의 Bot 호출 결과다(후속 요구사항 문서 §12).
 *
 * 실패의 세부 원인은 `errorCode`와 `retryable`에 남긴다. Discord SDK의 raw Error나 Stack
 * Trace, 전체 Request Payload는 저장하지 않는다(§12·§16).
 */
enum class DiscordDeliveryAttemptResult {
    SUCCESS,
    FAILURE,
}
