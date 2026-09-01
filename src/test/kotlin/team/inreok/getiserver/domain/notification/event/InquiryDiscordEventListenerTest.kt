package team.inreok.getiserver.domain.notification.event

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import team.inreok.getiserver.domain.inquiry.entity.type.InquiryType
import team.inreok.getiserver.domain.inquiry.event.InquiryCreatedEvent
import team.inreok.getiserver.domain.notification.dto.DiscordDeliveryEnqueueCommand
import team.inreok.getiserver.domain.notification.entity.type.DiscordMessageTemplate
import team.inreok.getiserver.domain.notification.service.DiscordDeliveryService
import team.inreok.getiserver.global.discord.DiscordChannelResolver
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InquiryDiscordEventListenerTest {
    @Mock
    private lateinit var discordChannelResolver: DiscordChannelResolver

    @Mock
    private lateinit var discordDeliveryService: DiscordDeliveryService

    private val listener by lazy {
        InquiryDiscordEventListener(discordChannelResolver, discordDeliveryService)
    }

    private val event =
        InquiryCreatedEvent(
            inquiryId = 1L,
            inquiryType = InquiryType.ERROR,
            title = "로그인이 안 됩니다",
            authorMemberId = 7L,
            occurredAt = LocalDateTime.of(2026, 8, 10, 9, 0),
        )

    @Test
    fun `고정 채널로 INQUIRY_CREATED Delivery를 예약한다`() {
        given(discordChannelResolver.resolveInquiryChannelId()).willReturn("channel-alert")

        listener.onInquiryCreated(event)

        val captor = ArgumentCaptor.forClass(DiscordDeliveryEnqueueCommand::class.java)
        // capture()가 반환하는 Java platform type을 non-null 파라미터에 그대로 넘기면 Kotlin이
        // 끼워 넣은 호출부 null 검사 때문에 NPE가 난다. Elvis 기본값으로 우회한다.
        verify(discordDeliveryService).enqueue(
            captor.capture() ?: DiscordDeliveryEnqueueCommand(DiscordMessageTemplate.INQUIRY_CREATED, 0L, ""),
        )
        val command = captor.value
        assertThat(command.template).isEqualTo(DiscordMessageTemplate.INQUIRY_CREATED)
        assertThat(command.targetId).isEqualTo(1L)
        assertThat(command.channelId).isEqualTo("channel-alert")
        // 문의 접수 알림은 학년 대상이 아닌 관리자 Alert라 Mention하지 않는다.
        assertThat(command.roleIds).isEmpty()
    }

    @Test
    fun `채널이 설정되지 않았으면 예약하지 않는다`() {
        listener.onInquiryCreated(event)

        verifyNoInteractions(discordDeliveryService)
    }

    @Test
    fun `예약이 실패해도 예외를 다시 던지지 않는다`() {
        given(discordChannelResolver.resolveInquiryChannelId()).willReturn("channel-alert")
        given(
            discordDeliveryService.enqueue(
                DiscordDeliveryEnqueueCommand(DiscordMessageTemplate.INQUIRY_CREATED, 1L, "channel-alert"),
            ),
        ).willThrow(RuntimeException("db down"))

        listener.onInquiryCreated(event)
    }
}
