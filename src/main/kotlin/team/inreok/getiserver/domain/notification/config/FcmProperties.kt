package team.inreok.getiserver.domain.notification.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * FCM HTTP v1 API 연동 설정이다(application.yaml의 `app.push.fcm.*`, Issue #190).
 *
 * [serviceAccountKey]는 Firebase(Google Cloud) Service Account의 전체 JSON Key 문서를 그대로
 * 담은 값이다(`project_id`/`client_email`/`private_key`/`token_uri` 포함). 하나의 환경변수
 * (`PUSH_FCM_SERVICE_ACCOUNT_KEY`)로만 주입하고, 코드·문서·Test Fixture 어디에도 실제 값을 두지
 * 않는다(.claude/rules/security.md). [enabled]가 false거나 이 값이 비어 있으면
 * [isConfigured]가 false를 반환해 애플리케이션 기동과 알림 생성 API는 그대로 정상 동작하고
 * Push만 PENDING으로 쌓인다(Fail-Fast 아님, `DiscordBotProperties`와 같은 방식).
 *
 * `project_id`는 이 JSON 안에서만 얻는다 -- 별도 Property로 중복해서 요구하지 않는다.
 */
@ConfigurationProperties(prefix = "app.push.fcm")
data class FcmProperties(
    val enabled: Boolean = false,
    val serviceAccountKey: String = "",
    val connectTimeoutMs: Long = DEFAULT_CONNECT_TIMEOUT_MS,
    val readTimeoutMs: Long = DEFAULT_READ_TIMEOUT_MS,
) {
    fun isConfigured(): Boolean = enabled && serviceAccountKey.isNotBlank()

    companion object {
        private const val DEFAULT_CONNECT_TIMEOUT_MS = 3_000L
        private const val DEFAULT_READ_TIMEOUT_MS = 8_000L
    }
}
