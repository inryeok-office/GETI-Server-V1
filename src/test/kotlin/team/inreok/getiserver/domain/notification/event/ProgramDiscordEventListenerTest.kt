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
import team.inreok.getiserver.domain.notification.dto.DiscordDeliveryEnqueueCommand
import team.inreok.getiserver.domain.notification.entity.type.DiscordMessageTemplate
import team.inreok.getiserver.domain.notification.service.DiscordDeliveryService
import team.inreok.getiserver.domain.program.event.ProgramDiscordAction
import team.inreok.getiserver.domain.program.event.ProgramDiscordEvent
import team.inreok.getiserver.domain.program.query.ProgramDiscordPayloadQueryPort
import team.inreok.getiserver.domain.program.query.ProgramDiscordPayloadSnapshot
import team.inreok.getiserver.global.discord.DiscordChannelResolver
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProgramDiscordEventListenerTest {
    @Mock
    private lateinit var programDiscordPayloadQueryPort: ProgramDiscordPayloadQueryPort

    @Mock
    private lateinit var discordChannelResolver: DiscordChannelResolver

    @Mock
    private lateinit var discordDeliveryService: DiscordDeliveryService

    private val listener by lazy {
        ProgramDiscordEventListener(programDiscordPayloadQueryPort, discordChannelResolver, discordDeliveryService)
    }

    private val updatedAt = LocalDateTime.of(2026, 8, 10, 9, 0)

    @Test
    fun `PUBLISHED는 PROGRAM_PUBLISHED Template과 대상 학년 Role로 예약한다`() {
        given(programDiscordPayloadQueryPort.findById(1L)).willReturn(snapshot(targetGrades = listOf(2, 3)))
        given(discordChannelResolver.resolveProgramChannelId("channel-1")).willReturn("channel-1")
        given(discordChannelResolver.roleIdsForGrades(listOf(2, 3))).willReturn(listOf("role-2", "role-3"))

        listener.onProgramDiscordEvent(ProgramDiscordEvent(1L, ProgramDiscordAction.PUBLISHED))

        val command = captureCommand()
        assertThat(command.template).isEqualTo(DiscordMessageTemplate.PROGRAM_PUBLISHED)
        assertThat(command.targetId).isEqualTo(1L)
        assertThat(command.channelId).isEqualTo("channel-1")
        assertThat(command.roleIds).containsExactly("role-2", "role-3")
        assertThat(command.sourceUpdatedAt).isNull()
    }

    @Test
    fun `UPDATED는 원본 수정 시각을 함께 전달하고 Mention하지 않는다`() {
        given(programDiscordPayloadQueryPort.findById(1L)).willReturn(snapshot(updatedAt = updatedAt))
        given(discordChannelResolver.resolveProgramChannelId("channel-1")).willReturn("channel-1")

        listener.onProgramDiscordEvent(ProgramDiscordEvent(1L, ProgramDiscordAction.UPDATED))

        val command = captureCommand()
        assertThat(command.template).isEqualTo(DiscordMessageTemplate.PROGRAM_UPDATED)
        assertThat(command.sourceUpdatedAt).isEqualTo(updatedAt)
        assertThat(command.roleIds).isEmpty()
    }

    @Test
    fun `DELETED는 PROGRAM_DELETED Template로 예약한다`() {
        given(programDiscordPayloadQueryPort.findById(1L)).willReturn(snapshot())
        given(discordChannelResolver.resolveProgramChannelId("channel-1")).willReturn("channel-1")

        listener.onProgramDiscordEvent(ProgramDiscordEvent(1L, ProgramDiscordAction.DELETED))

        assertThat(captureCommand().template).isEqualTo(DiscordMessageTemplate.PROGRAM_DELETED)
    }

    @Test
    fun `대상을 찾을 수 없으면 예약하지 않는다`() {
        given(programDiscordPayloadQueryPort.findById(1L)).willReturn(null)

        listener.onProgramDiscordEvent(ProgramDiscordEvent(1L, ProgramDiscordAction.PUBLISHED))

        verifyNoInteractions(discordDeliveryService)
    }

    @Test
    fun `채널이 설정되지 않았으면 예약하지 않는다`() {
        given(programDiscordPayloadQueryPort.findById(1L)).willReturn(snapshot(discordChannelId = null))

        listener.onProgramDiscordEvent(ProgramDiscordEvent(1L, ProgramDiscordAction.PUBLISHED))

        verifyNoInteractions(discordDeliveryService)
    }

    @Test
    fun `예약이 실패해도 예외를 다시 던지지 않는다`() {
        given(programDiscordPayloadQueryPort.findById(1L)).willReturn(snapshot())
        given(discordChannelResolver.resolveProgramChannelId("channel-1")).willReturn("channel-1")
        given(
            discordDeliveryService.enqueue(
                DiscordDeliveryEnqueueCommand(DiscordMessageTemplate.PROGRAM_PUBLISHED, 1L, "channel-1"),
            ),
        ).willThrow(RuntimeException("db down"))

        listener.onProgramDiscordEvent(ProgramDiscordEvent(1L, ProgramDiscordAction.PUBLISHED))
    }

    private fun captureCommand(): DiscordDeliveryEnqueueCommand {
        val captor = ArgumentCaptor.forClass(DiscordDeliveryEnqueueCommand::class.java)
        // capture()가 반환하는 Java platform type을 non-null 파라미터에 그대로 넘기면 Kotlin이
        // 끼워 넣은 호출부 null 검사 때문에 NPE가 난다. Elvis 기본값으로 우회한다.
        verify(discordDeliveryService).enqueue(
            captor.capture() ?: DiscordDeliveryEnqueueCommand(DiscordMessageTemplate.PROGRAM_PUBLISHED, 0L, ""),
        )
        return captor.value
    }

    private fun snapshot(
        discordChannelId: String? = "channel-1",
        targetGrades: List<Int> = emptyList(),
        updatedAt: LocalDateTime = this.updatedAt,
    ) = ProgramDiscordPayloadSnapshot(
        programId = 1L,
        title = "여름 캠프",
        bodyMarkdown = "설명",
        eventStartedAt = null,
        eventEndedAt = null,
        discordChannelId = discordChannelId,
        targetGrades = targetGrades,
        updatedAt = updatedAt,
    )
}
