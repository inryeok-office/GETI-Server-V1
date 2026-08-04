package team.inreok.getiserver.domain.collector.notification

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * `DISCORD_JOB_NOTIFICATION_ENABLED`/`DISCORD_JOB_WEBHOOK_URL`/`DISCORD_JOB_NOTIFY_INITIAL_IMPORT`
 * 환경변수가 바인딩되는 대상이다(application.yaml의 `app.discord.job-notification.*`). 세 값
 * 모두 기본값이 있어(false/빈 문자열/false) 설정하지 않아도 애플리케이션·Collector·공고 등록은
 * 정상 동작하고, 알림만 비활성 상태로 유지된다(Issue #62 확장 범위).
 */
@ConfigurationProperties(prefix = "app.discord.job-notification")
data class DiscordJobNotificationProperties(
    val enabled: Boolean = false,
    val webhookUrl: String = "",
    // 최초 대량 수집(Provider의 첫 성공 Run) 시점에도 신규 공고 각각을 알림으로 보낼지 여부.
    // 기본 false — 첫 전체 수집은 수백 건일 수 있어 Discord Rate Limit/스팸을 피한다.
    val notifyInitialImport: Boolean = false,
) {
    fun isConfigured(): Boolean = enabled && webhookUrl.isNotBlank()
}
