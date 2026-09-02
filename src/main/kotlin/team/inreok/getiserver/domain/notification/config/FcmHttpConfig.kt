package team.inreok.getiserver.domain.notification.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder
import org.springframework.boot.http.client.HttpClientSettings
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient
import java.time.Duration

/**
 * Google OAuth2 Token 발급과 FCM HTTP v1 전송에 함께 쓰는 [RestClient]를 만든다
 * (`DiscordBotHttpConfig`와 같은 이유·같은 구조 -- Builder 조립을 분리해 Contract Test가
 * `MockRestServiceServer.bindTo(builder)`로 Mock을 심을 수 있게 한다).
 *
 * 두 호출(Token 발급 `oauth2.googleapis.com`, 메시지 전송 `fcm.googleapis.com`)이 서로 다른
 * Host를 향하므로 Base URL을 고정하지 않고, 호출부(`FcmAccessTokenProvider`/`FcmPushProvider`)가
 * 매번 전체 URI를 넘긴다.
 */
@Configuration
@EnableConfigurationProperties(FcmProperties::class)
class FcmHttpConfig {
    @Bean(FCM_REST_CLIENT)
    fun fcmRestClient(
        restClientBuilder: RestClient.Builder,
        properties: FcmProperties,
    ): RestClient =
        restClientBuilder
            .requestFactory(
                ClientHttpRequestFactoryBuilder.detect().build(
                    HttpClientSettings
                        .defaults()
                        .withConnectTimeout(Duration.ofMillis(properties.connectTimeoutMs))
                        .withReadTimeout(Duration.ofMillis(properties.readTimeoutMs)),
                ),
            ).build()

    companion object {
        const val FCM_REST_CLIENT = "fcmRestClient"
    }
}
