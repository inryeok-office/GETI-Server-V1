package team.inreok.getiserver.domain.notification.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder
import org.springframework.boot.http.client.HttpClientSettings
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient
import java.time.Duration

/**
 * GETI-Bot-V1 Internal API 전용 [RestClient]를 만든다.
 *
 * `HttpDiscordBotClient`가 [RestClient.Builder]를 직접 받아 내부에서 조립하지 않고 이렇게
 * 분리한 이유는 **Contract Test 때문**이다. `MockRestServiceServer.bindTo(builder)`는 Builder에
 * Mock Request Factory를 심는 방식이라, Client가 생성자에서 다시 `requestFactory(...)`를 호출하면
 * 그 Mock이 덮여 요청을 가로챌 수 없다. 조립을 여기로 빼면 Test는 Mock을 심은 Builder로 만든
 * [RestClient]를 그대로 넘길 수 있다.
 *
 * Bean 이름을 지정해 다른 곳에서 `RestClient`를 주입할 때 이 Bot 전용 Client가 실수로 선택되지
 * 않게 한다 -- Timeout이 Bot 기준으로 맞춰져 있어 범용으로 쓰면 안 된다.
 */
@Configuration
@EnableConfigurationProperties(DiscordBotProperties::class)
class DiscordBotHttpConfig {
    @Bean(DISCORD_BOT_REST_CLIENT)
    fun discordBotRestClient(
        restClientBuilder: RestClient.Builder,
        properties: DiscordBotProperties,
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
        const val DISCORD_BOT_REST_CLIENT = "discordBotRestClient"
    }
}
