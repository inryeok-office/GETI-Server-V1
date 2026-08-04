package team.inreok.getiserver.domain.collector.notification.service.impl

import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import team.inreok.getiserver.domain.collector.entity.JobNotificationDelivery
import team.inreok.getiserver.domain.collector.entity.type.JobNotificationDeliveryStatus
import team.inreok.getiserver.domain.collector.notification.DiscordJobNotificationProperties
import team.inreok.getiserver.domain.collector.notification.discord.DiscordJobEmbedBuilder
import team.inreok.getiserver.domain.collector.notification.discord.DiscordWebhookClient
import team.inreok.getiserver.domain.collector.notification.discord.DiscordWebhookSendResult
import team.inreok.getiserver.domain.collector.notification.discord.JobNotificationEmbedInput
import team.inreok.getiserver.domain.collector.notification.service.JobNotificationService
import team.inreok.getiserver.domain.collector.notification.service.JobNotificationTrigger
import team.inreok.getiserver.domain.collector.repository.JobNotificationDeliveryRepository
import java.time.LocalDateTime

/**
 * Delivery 생성(짧은 Transaction)과 Discord HTTP 발송(Transaction 밖)을 분리한다. 이 Class
 * 자체에는 `@Transactional`을 붙이지 않는다 — 각 `saveAndFlush` 호출이 독립된 짧은 Transaction으로
 * 끝나고, 그 사이 HTTP 호출은 어떤 DB Transaction에도 묶이지 않는다(Issue #62 확장 범위, "Discord
 * 발송과 DB 저장을 같은 Transaction에 묶지 않는다").
 */
@Service
@EnableConfigurationProperties(DiscordJobNotificationProperties::class)
class JobNotificationServiceImpl(
    private val properties: DiscordJobNotificationProperties,
    private val deliveryRepository: JobNotificationDeliveryRepository,
    private val discordWebhookClient: DiscordWebhookClient,
) : JobNotificationService {
    // 알림을 건너뛰는 조건(미설정/초기 수집 억제/중복)마다 조기 반환하는 가드 절이 중첩 if보다
    // 읽기 쉽다고 판단해 ReturnCount를 그대로 둔다.
    @Suppress("ReturnCount")
    override fun enqueueIfEligible(
        trigger: JobNotificationTrigger,
        isInitialImport: Boolean,
    ) {
        if (!properties.isConfigured()) return
        if (isInitialImport && !properties.notifyInitialImport) return
        if (deliveryRepository.existsByJobId(trigger.jobId)) return

        val delivery = createDeliveryOrNull(trigger) ?: return
        attemptSend(delivery)
    }

    override fun processDueRetries() {
        if (!properties.isConfigured()) return
        deliveryRepository
            .findDueForRetry(LocalDateTime.now())
            .filter { it.attemptCount < MAX_ATTEMPTS }
            .forEach { attemptSend(it) }
    }

    private fun createDeliveryOrNull(trigger: JobNotificationTrigger): JobNotificationDelivery? =
        try {
            deliveryRepository.saveAndFlush(
                JobNotificationDelivery(
                    jobId = trigger.jobId,
                    sourceCode = trigger.sourceCode,
                    sourceDisplayName = trigger.sourceDisplayName,
                    title = trigger.title,
                    companyName = trigger.companyName,
                    externalUrl = trigger.externalUrl,
                    recruitmentEndedAt = trigger.recruitmentEndedAt,
                    employmentType = trigger.employmentType,
                    educationCondition = trigger.educationCondition,
                    careerCondition = trigger.careerCondition,
                    relevanceCategory = trigger.relevanceCategory,
                ),
            )
        } catch (ex: DataIntegrityViolationException) {
            // job_id UNIQUE 위반 — 동시 호출 등으로 이미 생성된 경우다. 조용히 건너뛴다(멱등).
            log.debug("이미 생성된 알림 Delivery라 재생성하지 않습니다: jobId={}", trigger.jobId, ex)
            null
        }

    private fun attemptSend(delivery: JobNotificationDelivery) {
        delivery.status = JobNotificationDeliveryStatus.SENDING
        delivery.attemptCount += 1
        delivery.lastAttemptAt = LocalDateTime.now()
        deliveryRepository.saveAndFlush(delivery)

        val payload = DiscordJobEmbedBuilder.buildPayload(toEmbedInput(delivery))

        when (val result = discordWebhookClient.send(properties.webhookUrl, payload)) {
            is DiscordWebhookSendResult.Sent -> {
                markSent(delivery, result)
            }

            is DiscordWebhookSendResult.Retryable -> {
                markRetryable(delivery, result)
            }

            is DiscordWebhookSendResult.NonRetryable -> {
                markFailedFinal(delivery, result.reason, "DISCORD_NON_RETRYABLE")
            }
        }
    }

    private fun toEmbedInput(delivery: JobNotificationDelivery) =
        JobNotificationEmbedInput(
            jobId = delivery.jobId,
            sourceDisplayName = delivery.sourceDisplayName,
            title = delivery.title,
            companyName = delivery.companyName,
            externalUrl = delivery.externalUrl,
            recruitmentEndedAt = delivery.recruitmentEndedAt,
            notifiedAt = LocalDateTime.now(),
            employmentType = delivery.employmentType,
            educationCondition = delivery.educationCondition,
            careerCondition = delivery.careerCondition,
            relevanceCategory = delivery.relevanceCategory,
        )

    private fun markSent(
        delivery: JobNotificationDelivery,
        result: DiscordWebhookSendResult.Sent,
    ) {
        delivery.status = JobNotificationDeliveryStatus.SENT
        delivery.sentAt = LocalDateTime.now()
        delivery.discordMessageId = result.discordMessageId
        delivery.errorCode = null
        delivery.errorMessage = null
        delivery.nextRetryAt = null
        deliveryRepository.saveAndFlush(delivery)
    }

    private fun markRetryable(
        delivery: JobNotificationDelivery,
        result: DiscordWebhookSendResult.Retryable,
    ) {
        if (delivery.attemptCount >= MAX_ATTEMPTS) {
            markFailedFinal(delivery, result.reason, "DISCORD_MAX_ATTEMPTS_EXCEEDED")
            return
        }
        delivery.status = JobNotificationDeliveryStatus.FAILED
        delivery.errorCode = "DISCORD_RETRYABLE"
        delivery.errorMessage = result.reason.take(MAX_ERROR_MESSAGE_LENGTH)
        val delaySeconds = result.retryAfterSeconds ?: backoffSeconds(delivery.attemptCount)
        delivery.nextRetryAt = LocalDateTime.now().plusSeconds(delaySeconds)
        deliveryRepository.saveAndFlush(delivery)
    }

    private fun markFailedFinal(
        delivery: JobNotificationDelivery,
        reason: String,
        errorCode: String,
    ) {
        delivery.status = JobNotificationDeliveryStatus.FAILED
        delivery.errorCode = errorCode
        delivery.errorMessage = reason.take(MAX_ERROR_MESSAGE_LENGTH)
        delivery.nextRetryAt = null
        deliveryRepository.saveAndFlush(delivery)
    }

    private fun backoffSeconds(attemptCount: Int): Long =
        (BASE_BACKOFF_SECONDS * attemptCount).coerceAtMost(MAX_BACKOFF_SECONDS)

    private companion object {
        const val MAX_ATTEMPTS = 5
        const val MAX_ERROR_MESSAGE_LENGTH = 500
        const val BASE_BACKOFF_SECONDS = 60L
        const val MAX_BACKOFF_SECONDS = 1_800L
        val log = LoggerFactory.getLogger(JobNotificationServiceImpl::class.java)
    }
}
