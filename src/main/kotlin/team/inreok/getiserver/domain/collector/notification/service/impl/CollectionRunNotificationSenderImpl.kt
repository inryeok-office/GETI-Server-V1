package team.inreok.getiserver.domain.collector.notification.service.impl

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import team.inreok.getiserver.domain.collector.notification.DiscordJobNotificationProperties
import team.inreok.getiserver.domain.collector.notification.discord.CollectionRunSummaryEmbedInput
import team.inreok.getiserver.domain.collector.notification.discord.DiscordCollectionRunEmbedBuilder
import team.inreok.getiserver.domain.collector.notification.discord.DiscordWebhookClient
import team.inreok.getiserver.domain.collector.notification.discord.DiscordWebhookSendResult
import team.inreok.getiserver.domain.collector.notification.service.CollectionRunNotificationSender

@Component
class CollectionRunNotificationSenderImpl(
    private val properties: DiscordJobNotificationProperties,
    private val discordWebhookClient: DiscordWebhookClient,
) : CollectionRunNotificationSender {
    override fun notify(input: CollectionRunSummaryEmbedInput) {
        if (!properties.isConfigured()) return

        val payload = DiscordCollectionRunEmbedBuilder.buildPayload(input)
        when (val result = discordWebhookClient.send(properties.webhookUrl, payload)) {
            is DiscordWebhookSendResult.Sent -> {
                log.debug("Collection Run 요약 알림 전송 성공: runId={}", input.runId)
            }

            is DiscordWebhookSendResult.Retryable -> {
                log.warn(
                    "Collection Run 요약 알림 전송 실패(재시도 가능, 재시도하지 않음): runId={}, reason={}",
                    input.runId,
                    result.reason,
                )
            }

            is DiscordWebhookSendResult.NonRetryable -> {
                log.warn("Collection Run 요약 알림 전송 실패: runId={}, reason={}", input.runId, result.reason)
            }
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(CollectionRunNotificationSenderImpl::class.java)
    }
}
