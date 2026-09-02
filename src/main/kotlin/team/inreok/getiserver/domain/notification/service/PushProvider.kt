package team.inreok.getiserver.domain.notification.service

import team.inreok.getiserver.domain.notification.entity.type.PushPlatform

/**
 * Notification 도메인이 소유하는 모바일 Push Outbound Port다(Issue #190, `DiscordBotClient`와
 * 같은 방식). 실제 전송은 `domain.notification.service.impl.FcmPushProvider`가 FCM HTTP v1 API로
 * 수행하고, Test는 Fake 구현으로 대체한다.
 *
 * iOS/Android를 위한 별도 APNs 직접 연동은 두지 않는다 -- 저장소·설정 어디에도 APNs 직접 연동이
 * 필요하다는 근거가 없어(DECISION_REQUIRED, PR 본문 참고) FCM 단일 Gateway로 두 Platform을 모두
 * 처리한다(iOS Token도 FCM에 등록해 FCM이 내부적으로 APNs에 전달하는 표준 구성).
 */
fun interface PushProvider {
    fun send(command: PushSendCommand): PushSendResult
}

/** Push 전송 요청 하나다. [data]는 Notification Click 시 App이 화면을 이동하는 데 쓰는 최소 정보다. */
data class PushSendCommand(
    val platform: PushPlatform,
    val token: String,
    val title: String,
    val body: String,
    val data: Map<String, String>,
)

/**
 * Provider 호출 결과다. 실패를 예외로 던지지 않고 값으로 돌려준다(`DiscordBotResult`와 같은
 * 이유 -- Worker가 [Failure.retryable]/[Failure.invalidToken]을 보고 다음 상태를 계산하는 것이
 * 정상 흐름이다).
 */
sealed interface PushSendResult {
    data class Success(
        val providerMessageId: String?,
    ) : PushSendResult

    /**
     * @param code Provider가 분류한 오류 코드이거나 서버가 자체적으로 분류한 코드.
     * @param retryable 재시도로 해결될 수 있는 실패인지.
     * @param invalidToken 기기의 Push Token 자체가 더 이상 유효하지 않은 실패인지(예: FCM
     *   `UNREGISTERED`). true면 호출부가 해당 `NotificationDevice` 등록을 제거한다(Issue #190
     *   확정 계약). true이면 [retryable]은 항상 false다 -- 같은 Token으로는 재시도해도 결과가
     *   달라지지 않는다.
     * @param message 운영자가 원인을 파악할 최소한의 문구. Provider의 raw Error나 Stack Trace를
     *   담지 않는다.
     */
    data class Failure(
        val code: String,
        val retryable: Boolean,
        val invalidToken: Boolean,
        val message: String?,
    ) : PushSendResult
}
