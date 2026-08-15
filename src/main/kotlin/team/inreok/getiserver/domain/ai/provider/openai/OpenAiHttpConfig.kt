package team.inreok.getiserver.domain.ai.provider.openai

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder
import org.springframework.boot.http.client.HttpClientSettings
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient
import java.time.Duration

/**
 * OpenAI 전용 [RestClient]를 만든다(`DiscordBotHttpConfig`와 같은 이유로 분리). Timeout이 OpenAI
 * 기준으로 맞춰져 있어 다른 곳에서 범용 [RestClient]로 실수로 주입받지 않도록 Bean 이름을
 * 지정한다.
 */
@Configuration
@EnableConfigurationProperties(OpenAiProperties::class)
class OpenAiHttpConfig {
    @Bean(OPENAI_REST_CLIENT)
    fun openAiRestClient(
        restClientBuilder: RestClient.Builder,
        properties: OpenAiProperties,
    ): RestClient =
        restClientBuilder
            .baseUrl(properties.baseUrl)
            .requestFactory(
                ClientHttpRequestFactoryBuilder.detect().build(
                    HttpClientSettings
                        .defaults()
                        .withConnectTimeout(Duration.ofMillis(properties.connectTimeoutMs))
                        .withReadTimeout(Duration.ofMillis(properties.readTimeoutMs)),
                ),
            ).build()

    companion object {
        const val OPENAI_REST_CLIENT = "openAiRestClient"
    }
}
