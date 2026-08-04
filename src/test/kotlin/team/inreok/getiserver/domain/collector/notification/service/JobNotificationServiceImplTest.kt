package team.inreok.getiserver.domain.collector.notification.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import team.inreok.getiserver.domain.collector.entity.JobNotificationDelivery
import team.inreok.getiserver.domain.collector.entity.type.JobNotificationDeliveryStatus
import team.inreok.getiserver.domain.collector.notification.DiscordJobNotificationProperties
import team.inreok.getiserver.domain.collector.notification.discord.DiscordWebhookClient
import team.inreok.getiserver.domain.collector.notification.discord.DiscordWebhookSendResult
import team.inreok.getiserver.domain.collector.notification.service.impl.JobNotificationServiceImpl
import team.inreok.getiserver.domain.collector.repository.JobNotificationDeliveryRepository
import java.time.LocalDateTime

/**
 * 실제 Discord 서버 없이 DiscordWebhookClient를 Mock으로 대체해 조건 판정(설정/초기 수집 억제/
 * 중복)과 Transaction 분리(Delivery 저장 -> HTTP 발송 -> 결과 저장)만 검증한다.
 */
@ExtendWith(MockitoExtension::class)
class JobNotificationServiceImplTest {
    @Mock
    private lateinit var deliveryRepository: JobNotificationDeliveryRepository

    @Mock
    private lateinit var discordWebhookClient: DiscordWebhookClient

    private fun serviceWith(properties: DiscordJobNotificationProperties) =
        JobNotificationServiceImpl(properties, deliveryRepository, discordWebhookClient)

    private val configuredProperties =
        DiscordJobNotificationProperties(enabled = true, webhookUrl = "https://discord.com/api/webhooks/1/token")

    private fun triggerOf(jobId: Long = 1L) =
        JobNotificationTrigger(
            jobId = jobId,
            sourceCode = "MMA",
            sourceDisplayName = "병역일터",
            title = "백엔드 개발자",
            companyName = "인력개발원",
            externalUrl = null,
            recruitmentEndedAt = null,
        )

    // Kotlin non-null 파라미터에 bare any()를 쓰면 null 반환으로 NPE가 나므로(CompanyServiceTest와
    // 동일한 이유) 모든 Matcher Helper에 Elvis 기본값을 채운다.
    private fun anyDelivery(): JobNotificationDelivery =
        any(JobNotificationDelivery::class.java)
            ?: JobNotificationDelivery(
                jobId = 0,
                sourceCode = "x",
                sourceDisplayName = "x",
                title = "x",
                companyName = "x",
            )

    private fun anyPayload(): Map<String, Any?> = any<Map<String, Any?>>() ?: emptyMap()

    private fun anyStatusList(): List<JobNotificationDeliveryStatus> =
        any<List<JobNotificationDeliveryStatus>>() ?: emptyList()

    private fun anyDateTime(): LocalDateTime = any(LocalDateTime::class.java) ?: LocalDateTime.now()

    @Test
    fun `Discord 알림이 비활성이면 Delivery를 만들지 않는다`() {
        val service = serviceWith(DiscordJobNotificationProperties(enabled = false))

        service.enqueueIfEligible(triggerOf(), isInitialImport = false)

        verify(deliveryRepository, never()).saveAndFlush(anyDelivery())
    }

    @Test
    fun `초기 수집이고 초기 수집 알림이 꺼져 있으면 Delivery를 만들지 않는다`() {
        val service = serviceWith(configuredProperties.copy(notifyInitialImport = false))

        service.enqueueIfEligible(triggerOf(), isInitialImport = true)

        verify(deliveryRepository, never()).saveAndFlush(anyDelivery())
    }

    @Test
    fun `이미 같은 Job에 대한 Delivery가 있으면 다시 만들지 않는다`() {
        given(deliveryRepository.existsByJobId(1L)).willReturn(true)
        val service = serviceWith(configuredProperties)

        service.enqueueIfEligible(triggerOf(jobId = 1L), isInitialImport = false)

        verify(deliveryRepository, never()).saveAndFlush(anyDelivery())
    }

    @Test
    fun `발송에 성공하면 SENT 상태와 Discord Message ID를 저장한다`() {
        given(deliveryRepository.existsByJobId(1L)).willReturn(false)
        given(deliveryRepository.saveAndFlush(anyDelivery())).willAnswer { it.arguments[0] }
        given(discordWebhookClient.send(anyString(), anyPayload())).willReturn(DiscordWebhookSendResult.Sent("999"))
        val service = serviceWith(configuredProperties)

        service.enqueueIfEligible(triggerOf(jobId = 1L), isInitialImport = false)

        // 생성(1) -> SENDING 전환(2) -> SENT 확정(3), 총 세 번의 짧은 Transaction으로 나뉜다.
        verify(deliveryRepository, times(3)).saveAndFlush(anyDelivery())
    }

    @Test
    fun `재시도 가능한 오류면 FAILED로 남기고 다음 재시도 시각을 기록한다`() {
        given(deliveryRepository.existsByJobId(1L)).willReturn(false)
        val savedDeliveries = mutableListOf<JobNotificationDelivery>()
        given(deliveryRepository.saveAndFlush(anyDelivery())).willAnswer {
            (it.arguments[0] as JobNotificationDelivery).also(savedDeliveries::add)
        }
        given(discordWebhookClient.send(anyString(), anyPayload()))
            .willReturn(DiscordWebhookSendResult.Retryable("Rate Limited", retryAfterSeconds = 30))
        val service = serviceWith(configuredProperties)

        service.enqueueIfEligible(triggerOf(jobId = 1L), isInitialImport = false)

        val last = savedDeliveries.last()
        assertThat(last.status).isEqualTo(JobNotificationDeliveryStatus.FAILED)
        assertThat(last.nextRetryAt).isNotNull()
        assertThat(last.attemptCount).isEqualTo(1)
    }

    @Test
    fun `재시도 불가능한 오류면 FAILED로 남기고 다음 재시도 시각을 비운다`() {
        given(deliveryRepository.existsByJobId(1L)).willReturn(false)
        val savedDeliveries = mutableListOf<JobNotificationDelivery>()
        given(deliveryRepository.saveAndFlush(anyDelivery())).willAnswer {
            (it.arguments[0] as JobNotificationDelivery).also(savedDeliveries::add)
        }
        given(discordWebhookClient.send(anyString(), anyPayload()))
            .willReturn(DiscordWebhookSendResult.NonRetryable("잘못된 요청"))
        val service = serviceWith(configuredProperties)

        service.enqueueIfEligible(triggerOf(jobId = 1L), isInitialImport = false)

        val last = savedDeliveries.last()
        assertThat(last.status).isEqualTo(JobNotificationDeliveryStatus.FAILED)
        assertThat(last.nextRetryAt).isNull()
    }

    @Test
    fun `Discord 알림이 비활성이면 재시도 대상을 조회하지 않는다`() {
        val service = serviceWith(DiscordJobNotificationProperties(enabled = false))

        service.processDueRetries()

        verify(deliveryRepository, never()).findDueForRetry(anyStatusList(), anyDateTime())
    }

    @Test
    fun `최대 시도 횟수를 넘긴 Delivery는 재시도하지 않는다`() {
        val exhausted =
            JobNotificationDelivery(
                jobId = 1L,
                sourceCode = "MMA",
                sourceDisplayName = "병역일터",
                title = "x",
                companyName = "x",
            ).apply { attemptCount = 5 }
        given(deliveryRepository.findDueForRetry(anyStatusList(), anyDateTime())).willReturn(listOf(exhausted))
        val service = serviceWith(configuredProperties)

        service.processDueRetries()

        verify(discordWebhookClient, never()).send(anyString(), anyPayload())
    }

    @Test
    fun `재시도 대상이면 다시 발송을 시도한다`() {
        val due =
            JobNotificationDelivery(
                jobId = 1L,
                sourceCode = "MMA",
                sourceDisplayName = "병역일터",
                title = "x",
                companyName = "x",
            ).apply {
                status = JobNotificationDeliveryStatus.FAILED
                attemptCount = 1
                nextRetryAt = LocalDateTime.now().minusMinutes(1)
            }
        given(deliveryRepository.findDueForRetry(anyStatusList(), anyDateTime())).willReturn(listOf(due))
        given(deliveryRepository.saveAndFlush(anyDelivery())).willAnswer { it.arguments[0] }
        given(discordWebhookClient.send(anyString(), anyPayload())).willReturn(DiscordWebhookSendResult.Sent("1"))
        val service = serviceWith(configuredProperties)

        service.processDueRetries()

        verify(discordWebhookClient).send(anyString(), anyPayload())
        assertThat(due.status).isEqualTo(JobNotificationDeliveryStatus.SENT)
    }
}
