package team.inreok.getiserver.domain.notification.service.impl

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import org.springframework.data.domain.Pageable
import team.inreok.getiserver.domain.inquiry.query.InquiryDiscordPayloadQueryPort
import team.inreok.getiserver.domain.job.query.JobDiscordPayloadQueryPort
import team.inreok.getiserver.domain.notification.config.DiscordBotProperties
import team.inreok.getiserver.domain.notification.dto.DiscordDeliveryEnqueueCommand
import team.inreok.getiserver.domain.notification.entity.DiscordDelivery
import team.inreok.getiserver.domain.notification.entity.DiscordDeliveryAttempt
import team.inreok.getiserver.domain.notification.entity.type.DiscordDeliveryAttemptResult
import team.inreok.getiserver.domain.notification.entity.type.DiscordDeliveryAttemptType
import team.inreok.getiserver.domain.notification.entity.type.DiscordDeliveryStatus
import team.inreok.getiserver.domain.notification.entity.type.DiscordMessageTemplate
import team.inreok.getiserver.domain.notification.exception.DiscordDeliveryNotFoundException
import team.inreok.getiserver.domain.notification.exception.DiscordDeliveryNotRetryableException
import team.inreok.getiserver.domain.notification.exception.DiscordDeliveryRetryLimitExceededException
import team.inreok.getiserver.domain.notification.repository.DiscordDeliveryAttemptRepository
import team.inreok.getiserver.domain.notification.repository.DiscordDeliveryRepository
import team.inreok.getiserver.domain.notification.service.DiscordBotResult
import team.inreok.getiserver.domain.notification.service.DiscordDeliveryRetryPolicy
import team.inreok.getiserver.domain.notification.service.DiscordPayloadFactory
import team.inreok.getiserver.domain.notification.service.FakeDiscordBotClient
import team.inreok.getiserver.domain.program.query.ProgramDiscordPayloadQueryPort
import team.inreok.getiserver.domain.program.query.ProgramDiscordPayloadSnapshot
import java.time.LocalDateTime
import java.util.Optional

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DiscordDeliveryServiceImplTest {
    @Mock
    private lateinit var deliveryRepository: DiscordDeliveryRepository

    @Mock
    private lateinit var attemptRepository: DiscordDeliveryAttemptRepository

    @Mock
    private lateinit var jobPayloadQueryPort: JobDiscordPayloadQueryPort

    @Mock
    private lateinit var programPayloadQueryPort: ProgramDiscordPayloadQueryPort

    @Mock
    private lateinit var inquiryPayloadQueryPort: InquiryDiscordPayloadQueryPort

    private val botClient = FakeDiscordBotClient()
    private val properties =
        DiscordBotProperties(enabled = true, baseUrl = "http://bot:3000", internalApiKey = "key")

    private fun service() =
        DiscordDeliveryServiceImpl(
            deliveryRepository = deliveryRepository,
            attemptRepository = attemptRepository,
            botClient = botClient,
            payloadFactory = DiscordPayloadFactory(),
            retryPolicy = DiscordDeliveryRetryPolicy(properties),
            properties = properties,
            jobPayloadQueryPort = jobPayloadQueryPort,
            programPayloadQueryPort = programPayloadQueryPort,
            inquiryPayloadQueryPort = inquiryPayloadQueryPort,
        )

    // --- enqueue ---------------------------------------------------------

    @Test
    fun `Delivery를 생성하면 PENDING 상태로 시작한다`() {
        given(deliveryRepository.findByIdempotencyKey(anyKey())).willReturn(null)
        given(deliveryRepository.saveAndFlush(anyDelivery())).willAnswer { it.arguments[0].withId(1L) }

        service().enqueue(enqueueCommand())

        val saved = captureSaved()
        assertThat(saved.status).isEqualTo(DiscordDeliveryStatus.PENDING)
        assertThat(saved.automaticRetryCount).isZero()
        assertThat(saved.manualRetryCount).isZero()
        assertThat(saved.discordMessageId).isNull()
    }

    @Test
    fun `이미 예약된 Delivery가 있으면 새로 만들지 않고 기존 id를 돌려준다`() {
        val existing = delivery(id = 7L)
        given(deliveryRepository.findByIdempotencyKey(anyKey())).willReturn(existing)

        assertThat(service().enqueue(enqueueCommand())).isEqualTo(7L)
    }

    @Test
    fun `CREATE가 아닌 Delivery에는 Mention Role을 저장하지 않는다`() {
        given(deliveryRepository.findByIdempotencyKey(anyKey())).willReturn(null)
        given(deliveryRepository.saveAndFlush(anyDelivery())).willAnswer { it.arguments[0].withId(1L) }

        // Mention은 최초 성공 CREATE 한 번만 허용된다(요구사항 §24).
        service().enqueue(
            enqueueCommand(
                template = DiscordMessageTemplate.PROGRAM_CLOSED,
                roleIds = listOf("role-1", "role-2"),
            ),
        )

        assertThat(captureSaved().roleIds).isNull()
    }

    @Test
    fun `CREATE Delivery는 Mention Role을 Bot 상한인 10개까지만 저장한다`() {
        given(deliveryRepository.findByIdempotencyKey(anyKey())).willReturn(null)
        given(deliveryRepository.saveAndFlush(anyDelivery())).willAnswer { it.arguments[0].withId(1L) }

        service().enqueue(enqueueCommand(roleIds = (1..15).map { "role-$it" }))

        assertThat(captureSaved().roleIdList()).hasSize(10)
    }

    // --- 성공 처리 --------------------------------------------------------

    @Test
    fun `전송에 성공하면 DELIVERED가 되고 messageId를 저장한다`() {
        val delivery = claimable(delivery(id = 1L))
        botClient.willReturn(DiscordBotResult.Success("discord-99"))

        service().processDueDeliveries()

        assertThat(delivery.status).isEqualTo(DiscordDeliveryStatus.DELIVERED)
        assertThat(delivery.discordMessageId).isEqualTo("discord-99")
        assertThat(delivery.deliveredAt).isNotNull()
        assertThat(delivery.nextRetryAt).isNull()
    }

    @Test
    fun `성공하면 Attempt를 SUCCESS로 남긴다`() {
        claimable(delivery(id = 1L))
        botClient.willReturn(DiscordBotResult.Success("discord-99"))

        service().processDueDeliveries()

        assertThat(captureAttempt().result).isEqualTo(DiscordDeliveryAttemptResult.SUCCESS)
    }

    @Test
    fun `messageId 없이 성공하면 DELIVERED로 처리하지 않는다`() {
        // CREATE 성공인데 messageId가 없으면 이후 수정이 불가능하다(요구사항 §21).
        val delivery = claimable(delivery(id = 1L))
        botClient.willReturn(DiscordBotResult.Success("  "))

        service().processDueDeliveries()

        assertThat(delivery.status).isEqualTo(DiscordDeliveryStatus.FAILED)
        assertThat(delivery.discordMessageId).isNull()
    }

    // --- 실패와 재시도 ----------------------------------------------------

    @Test
    fun `재시도 가능한 실패는 PENDING으로 남고 다음 시도 시각이 예약된다`() {
        val delivery = claimable(delivery(id = 1L))
        botClient.willReturn(DiscordBotResult.Failure("RATE_LIMITED", retryable = true, message = "429"))

        service().processDueDeliveries()

        assertThat(delivery.status).isEqualTo(DiscordDeliveryStatus.PENDING)
        assertThat(delivery.automaticRetryCount).isEqualTo(1)
        assertThat(delivery.nextRetryAt).isNotNull()
        assertThat(delivery.lastErrorCode).isEqualTo("RATE_LIMITED")
    }

    @Test
    fun `재시도 불가능한 실패는 즉시 FAILED가 된다`() {
        val delivery = claimable(delivery(id = 1L))
        botClient.willReturn(DiscordBotResult.Failure("INVALID_REQUEST", retryable = false, message = null))

        service().processDueDeliveries()

        assertThat(delivery.status).isEqualTo(DiscordDeliveryStatus.FAILED)
        assertThat(delivery.nextRetryAt).isNull()
    }

    @Test
    fun `MESSAGE_NOT_FOUND는 FAILED로 두고 새 메시지를 만들지 않는다`() {
        // 수동 삭제된 메시지를 몰래 다시 만들면 중복 공지가 된다(요구사항 §23).
        val delivery =
            claimable(delivery(id = 1L, template = DiscordMessageTemplate.PROGRAM_UPDATED, messageId = "m-1"))
        botClient.willReturn(DiscordBotResult.Failure("MESSAGE_NOT_FOUND", retryable = false, message = null))

        service().processDueDeliveries()

        assertThat(delivery.status).isEqualTo(DiscordDeliveryStatus.FAILED)
        assertThat(botClient.createCommands).isEmpty()
    }

    @Test
    fun `자동 재시도를 모두 쓰면 FAILED가 된다`() {
        val delivery = claimable(delivery(id = 1L, automaticRetryCount = 3))
        botClient.willReturn(DiscordBotResult.Failure("DISCORD_UNAVAILABLE", retryable = true, message = null))

        service().processDueDeliveries()

        assertThat(delivery.status).isEqualTo(DiscordDeliveryStatus.FAILED)
        assertThat(delivery.nextRetryAt).isNull()
        // 상한을 넘는 값이 저장되면 상태 조회에 3/3이 아닌 4/3으로 노출된다.
        assertThat(delivery.automaticRetryCount).isEqualTo(3)
    }

    @Test
    fun `자동 재시도가 남아 있으면 세 번째 실패까지 PENDING을 유지한다`() {
        val delivery = claimable(delivery(id = 1L, automaticRetryCount = 2))
        botClient.willReturn(DiscordBotResult.Failure("DISCORD_UNAVAILABLE", retryable = true, message = null))

        service().processDueDeliveries()

        assertThat(delivery.status).isEqualTo(DiscordDeliveryStatus.PENDING)
        assertThat(delivery.automaticRetryCount).isEqualTo(3)
        assertThat(delivery.nextRetryAt).isNotNull()
    }

    @Test
    fun `재시도 불가능한 실패는 자동 재시도 횟수를 올리지 않는다`() {
        val delivery = claimable(delivery(id = 1L))
        botClient.willReturn(DiscordBotResult.Failure("INVALID_REQUEST", retryable = false, message = null))

        service().processDueDeliveries()

        assertThat(delivery.automaticRetryCount).isZero()
    }

    @Test
    fun `실패하면 Attempt에 재시도 가능 여부를 함께 남긴다`() {
        claimable(delivery(id = 1L))
        botClient.willReturn(DiscordBotResult.Failure("RATE_LIMITED", retryable = true, message = "429"))

        service().processDueDeliveries()

        val attempt = captureAttempt()
        assertThat(attempt.result).isEqualTo(DiscordDeliveryAttemptResult.FAILURE)
        assertThat(attempt.errorCode).isEqualTo("RATE_LIMITED")
        assertThat(attempt.retryable).isTrue()
    }

    // --- 사전 검증 실패 ---------------------------------------------------

    @Test
    fun `대상을 찾을 수 없으면 Bot을 호출하지 않고 FAILED가 된다`() {
        val delivery = claimable(delivery(id = 1L), payloadFound = false)

        service().processDueDeliveries()

        assertThat(delivery.status).isEqualTo(DiscordDeliveryStatus.FAILED)
        assertThat(delivery.lastErrorCode).isEqualTo("TARGET_NOT_FOUND")
        assertThat(botClient.callCount()).isZero()
    }

    @Test
    fun `수정할 messageId가 없으면 Bot을 호출하지 않고 FAILED가 된다`() {
        // 요구사항 §22 -- 자동으로 새 메시지를 만들지 않는다.
        val delivery =
            claimable(delivery(id = 1L, template = DiscordMessageTemplate.PROGRAM_UPDATED, messageId = null))

        service().processDueDeliveries()

        assertThat(delivery.status).isEqualTo(DiscordDeliveryStatus.FAILED)
        assertThat(delivery.lastErrorCode).isEqualTo("MISSING_DISCORD_MESSAGE_ID")
        assertThat(botClient.callCount()).isZero()
    }

    @Test
    fun `Bot을 호출하지 않은 실패는 Attempt를 남기지 않는다`() {
        // attempts는 실제 Bot 호출 이력이다(요구사항 §12).
        claimable(delivery(id = 1L), payloadFound = false)

        service().processDueDeliveries()

        org.mockito.Mockito
            .verify(attemptRepository, org.mockito.Mockito.never())
            .save(anyAttempt())
    }

    // --- 선점과 Stale 회수 ------------------------------------------------

    @Test
    fun `다른 인스턴스가 이미 선점했으면 전송하지 않는다`() {
        val delivery = delivery(id = 1L)
        given(deliveryRepository.findDueIds(anyStatus(), anyDateTime(), anyPageable())).willReturn(listOf(1L))
        given(deliveryRepository.claim(anyLong(), anyStatus(), anyStatus(), anyDateTime())).willReturn(0)
        given(deliveryRepository.findById(1L)).willReturn(Optional.of(delivery))

        assertThat(service().processDueDeliveries()).isZero()
        assertThat(botClient.callCount()).isZero()
    }

    @Test
    fun `Sweep은 Stale PROCESSING을 먼저 회수한다`() {
        given(deliveryRepository.findDueIds(anyStatus(), anyDateTime(), anyPageable())).willReturn(emptyList())

        service().processDueDeliveries()

        org.mockito.Mockito
            .verify(deliveryRepository)
            .recoverStaleProcessing(anyStatus(), anyStatus(), anyDateTime(), anyDateTime())
    }

    @Test
    fun `설정이 갖춰지지 않으면 아무것도 하지 않는다`() {
        val disabled =
            DiscordDeliveryServiceImpl(
                deliveryRepository = deliveryRepository,
                attemptRepository = attemptRepository,
                botClient = botClient,
                payloadFactory = DiscordPayloadFactory(),
                retryPolicy = DiscordDeliveryRetryPolicy(DiscordBotProperties()),
                properties = DiscordBotProperties(),
                jobPayloadQueryPort = jobPayloadQueryPort,
                programPayloadQueryPort = programPayloadQueryPort,
                inquiryPayloadQueryPort = inquiryPayloadQueryPort,
            )

        assertThat(disabled.processDueDeliveries()).isZero()
        org.mockito.Mockito.verifyNoInteractions(deliveryRepository)
    }

    // --- 수동 재시도 ------------------------------------------------------

    @Test
    fun `수동 재시도는 PENDING으로 되돌리고 자동 카운터를 초기화한다`() {
        val delivery =
            delivery(id = 1L, status = DiscordDeliveryStatus.FAILED, automaticRetryCount = 3)
        given(deliveryRepository.findById(1L)).willReturn(Optional.of(delivery))

        service().retryManually(1L)

        assertThat(delivery.status).isEqualTo(DiscordDeliveryStatus.PENDING)
        assertThat(delivery.manualRetryCount).isEqualTo(1)
        // 자동 백오프 사이클을 처음부터 다시 받는다. 이력은 attempts에 남아 있다.
        assertThat(delivery.automaticRetryCount).isZero()
        assertThat(delivery.nextRetryAt).isNull()
    }

    @Test
    fun `수동 재시도로 예약된 첫 전송은 MANUAL로 기록한다`() {
        val delivery = claimable(delivery(id = 1L).apply { manualRetryPending = true })
        botClient.willReturn(DiscordBotResult.Success("discord-99"))

        service().processDueDeliveries()

        assertThat(captureAttempt().attemptType).isEqualTo(DiscordDeliveryAttemptType.MANUAL)
    }

    @Test
    fun `수동 재시도 첫 전송이 끝나면 이후 전송은 다시 AUTOMATIC으로 기록한다`() {
        // 수동으로 시작한 시도가 재시도 가능 실패로 끝나면, 이어지는 백오프 재시도는 자동이다.
        val delivery = claimable(delivery(id = 1L).apply { manualRetryPending = true })
        botClient.willReturn(DiscordBotResult.Failure("RATE_LIMITED", retryable = true, message = null))

        service().processDueDeliveries()

        assertThat(delivery.manualRetryPending).isFalse()
    }

    @Test
    fun `자동 재시도로 예약된 전송은 AUTOMATIC으로 기록한다`() {
        claimable(delivery(id = 1L))
        botClient.willReturn(DiscordBotResult.Success("discord-99"))

        service().processDueDeliveries()

        assertThat(captureAttempt().attemptType).isEqualTo(DiscordDeliveryAttemptType.AUTOMATIC)
    }

    @Test
    fun `수동 재시도를 요청하면 다음 전송이 수동임을 표시한다`() {
        val delivery =
            delivery(id = 1L, status = DiscordDeliveryStatus.FAILED, automaticRetryCount = 3)
        given(deliveryRepository.findById(1L)).willReturn(Optional.of(delivery))

        service().retryManually(1L)

        assertThat(delivery.manualRetryPending).isTrue()
    }

    @Test
    fun `FAILED가 아니면 수동 재시도를 거부한다`() {
        val delivery = delivery(id = 1L, status = DiscordDeliveryStatus.DELIVERED)
        given(deliveryRepository.findById(1L)).willReturn(Optional.of(delivery))

        assertThatThrownBy { service().retryManually(1L) }
            .isInstanceOf(DiscordDeliveryNotRetryableException::class.java)
    }

    @Test
    fun `수동 재시도 상한을 모두 쓰면 거부한다`() {
        val delivery =
            delivery(id = 1L, status = DiscordDeliveryStatus.FAILED, manualRetryCount = 3)
        given(deliveryRepository.findById(1L)).willReturn(Optional.of(delivery))

        assertThatThrownBy { service().retryManually(1L) }
            .isInstanceOf(DiscordDeliveryRetryLimitExceededException::class.java)
    }

    @Test
    fun `없는 Delivery에 수동 재시도를 요청하면 404다`() {
        given(deliveryRepository.findById(anyLong())).willReturn(Optional.empty())

        assertThatThrownBy { service().retryManually(99L) }
            .isInstanceOf(DiscordDeliveryNotFoundException::class.java)
    }

    // --- Matcher Helper --------------------------------------------------

    // Kotlin non-null 파라미터에 bare any()를 쓰면 null 반환으로 NPE가 나므로
    // (JobNotificationServiceImplTest·CompanyServiceTest와 동일한 이유) Elvis 기본값을 채운다.
    private fun anyKey(): String = any(String::class.java) ?: ""

    private fun anyStatus(): DiscordDeliveryStatus =
        any(DiscordDeliveryStatus::class.java) ?: DiscordDeliveryStatus.PENDING

    private fun anyDateTime(): LocalDateTime = any(LocalDateTime::class.java) ?: LocalDateTime.now()

    private fun anyPageable(): Pageable = any(Pageable::class.java) ?: Pageable.unpaged()

    private fun anyDelivery(): DiscordDelivery = any(DiscordDelivery::class.java) ?: delivery(id = 0L)

    private fun anyAttempt(): DiscordDeliveryAttempt =
        any(DiscordDeliveryAttempt::class.java)
            ?: DiscordDeliveryAttempt(
                discordDeliveryId = 0L,
                attemptType = DiscordDeliveryAttemptType.AUTOMATIC,
                attemptNumber = 1,
                requestId = "x",
                startedAt = LocalDateTime.now(),
                finishedAt = LocalDateTime.now(),
                result = DiscordDeliveryAttemptResult.SUCCESS,
            )

    // --- Fixture ---------------------------------------------------------

    private fun enqueueCommand(
        template: DiscordMessageTemplate = DiscordMessageTemplate.PROGRAM_PUBLISHED,
        roleIds: List<String> = emptyList(),
    ) = DiscordDeliveryEnqueueCommand(
        template = template,
        targetId = 100L,
        channelId = "channel-1",
        roleIds = roleIds,
    )

    private fun delivery(
        id: Long,
        template: DiscordMessageTemplate = DiscordMessageTemplate.PROGRAM_PUBLISHED,
        status: DiscordDeliveryStatus = DiscordDeliveryStatus.PENDING,
        messageId: String? = null,
        automaticRetryCount: Int = 0,
        manualRetryCount: Int = 0,
    ) = DiscordDelivery(
        targetType = template.targetType,
        targetId = 100L,
        action = template.action,
        template = template,
        channelId = "channel-1",
        idempotencyKey = "PROGRAM:100:${template.action}",
    ).apply {
        this.id = id
        this.status = status
        this.discordMessageId = messageId
        this.automaticRetryCount = automaticRetryCount
        this.manualRetryCount = manualRetryCount
    }

    /** Sweep이 이 Delivery를 실제로 집어 처리하도록 Repository Mock을 준비한다. */
    private fun claimable(
        delivery: DiscordDelivery,
        payloadFound: Boolean = true,
    ): DiscordDelivery {
        val id = requireNotNull(delivery.id)
        given(deliveryRepository.findDueIds(anyStatus(), anyDateTime(), anyPageable())).willReturn(listOf(id))
        given(deliveryRepository.claim(anyLong(), anyStatus(), anyStatus(), anyDateTime())).willReturn(1)
        given(deliveryRepository.findById(id)).willReturn(Optional.of(delivery))
        given(programPayloadQueryPort.findById(anyLong()))
            .willReturn(
                if (payloadFound) {
                    ProgramDiscordPayloadSnapshot(
                        programId = 100L,
                        title = "여름 캠프",
                        bodyMarkdown = null,
                        eventStartedAt = LocalDateTime.of(2026, 8, 20, 9, 0),
                        eventEndedAt = null,
                    )
                } else {
                    null
                },
            )
        return delivery
    }

    private fun captureSaved(): DiscordDelivery {
        val captor = org.mockito.ArgumentCaptor.forClass(DiscordDelivery::class.java)
        org.mockito.Mockito
            .verify(deliveryRepository)
            .saveAndFlush(captor.capture())
        return captor.value
    }

    private fun captureAttempt(): DiscordDeliveryAttempt {
        val captor = org.mockito.ArgumentCaptor.forClass(DiscordDeliveryAttempt::class.java)
        org.mockito.Mockito
            .verify(attemptRepository)
            .save(captor.capture())
        return captor.value
    }

    private fun Any?.withId(id: Long): DiscordDelivery = (this as DiscordDelivery).apply { this.id = id }
}
