package team.inreok.getiserver.domain.notification.event

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import org.springframework.core.task.SyncTaskExecutor
import team.inreok.getiserver.domain.member.query.NotificationAudienceQueryPort
import team.inreok.getiserver.domain.notification.dto.NotificationCreateCommand
import team.inreok.getiserver.domain.notification.entity.type.NotificationTargetType
import team.inreok.getiserver.domain.notification.entity.type.NotificationType
import team.inreok.getiserver.domain.notification.service.NotificationService
import team.inreok.getiserver.domain.program.event.ProgramDiscordAction
import team.inreok.getiserver.domain.program.event.ProgramDiscordEvent
import team.inreok.getiserver.domain.program.query.ProgramDiscordPayloadQueryPort
import team.inreok.getiserver.domain.program.query.ProgramDiscordPayloadSnapshot
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProgramPublishedNotificationListenerTest {
    @Mock
    private lateinit var programDiscordPayloadQueryPort: ProgramDiscordPayloadQueryPort

    @Mock
    private lateinit var notificationAudienceQueryPort: NotificationAudienceQueryPort

    @Mock
    private lateinit var notificationService: NotificationService

    private val listener by lazy {
        ProgramPublishedNotificationListener(
            programDiscordPayloadQueryPort,
            notificationAudienceQueryPort,
            notificationService,
            SyncTaskExecutor(),
        )
    }

    private fun snapshot(targetGrades: List<Int> = emptyList()) =
        ProgramDiscordPayloadSnapshot(
            programId = 1L,
            title = "여름 캠프",
            bodyMarkdown = null,
            eventStartedAt = null,
            eventEndedAt = null,
            discordChannelId = "1234",
            targetGrades = targetGrades,
            updatedAt = LocalDateTime.of(2026, 8, 10, 9, 0),
        )

    @Test
    fun `PUBLISHED가 아니면 알림을 생성하지 않는다`() {
        listener.onProgramDiscordEvent(ProgramDiscordEvent(1L, ProgramDiscordAction.UPDATED))

        verifyNoInteractions(programDiscordPayloadQueryPort, notificationAudienceQueryPort, notificationService)
    }

    @Test
    fun `대상 학년이 있으면 그 학년 재학생 모두에게 알림을 생성한다`() {
        given(programDiscordPayloadQueryPort.findById(1L)).willReturn(snapshot(targetGrades = listOf(2, 3)))
        given(notificationAudienceQueryPort.findEligibleStudentIds(setOf(2, 3))).willReturn(listOf(10L, 11L))

        listener.onProgramDiscordEvent(ProgramDiscordEvent(1L, ProgramDiscordAction.PUBLISHED))

        val commands = captureCommands(2)
        assertThat(commands.map { it.recipientMemberId }).containsExactly(10L, 11L)
        assertThat(commands).allSatisfy { command ->
            assertThat(command.type).isEqualTo(NotificationType.PROGRAM_PUBLISHED)
            assertThat(command.targetType).isEqualTo(NotificationTargetType.PROGRAM)
            assertThat(command.targetId).isEqualTo(1L)
            assertThat(command.content).contains("여름 캠프")
            assertThat(command.sourceEventType).isEqualTo("ProgramDiscordEvent:PUBLISHED")
            assertThat(command.sourceEventId).isEqualTo(1L)
        }
    }

    @Test
    fun `대상 학년이 없으면 전 학년 조건으로 조회한다`() {
        given(programDiscordPayloadQueryPort.findById(1L)).willReturn(snapshot(targetGrades = emptyList()))
        given(notificationAudienceQueryPort.findEligibleStudentIds(emptySet())).willReturn(listOf(20L))

        listener.onProgramDiscordEvent(ProgramDiscordEvent(1L, ProgramDiscordAction.PUBLISHED))

        assertThat(captureCommands(1).single().recipientMemberId).isEqualTo(20L)
    }

    @Test
    fun `대상을 찾을 수 없으면 알림을 생성하지 않는다`() {
        given(programDiscordPayloadQueryPort.findById(1L)).willReturn(null)

        listener.onProgramDiscordEvent(ProgramDiscordEvent(1L, ProgramDiscordAction.PUBLISHED))

        verifyNoInteractions(notificationAudienceQueryPort, notificationService)
    }

    @Test
    fun `한 수신자의 알림 생성이 실패해도 나머지 수신자에게는 알림을 생성한다`() {
        given(programDiscordPayloadQueryPort.findById(1L)).willReturn(snapshot())
        given(notificationAudienceQueryPort.findEligibleStudentIds(emptySet())).willReturn(listOf(10L, 11L))
        given(notificationService.create(commandFor(10L))).willThrow(RuntimeException("db down"))

        listener.onProgramDiscordEvent(ProgramDiscordEvent(1L, ProgramDiscordAction.PUBLISHED))

        verify(notificationService).create(commandFor(11L))
    }

    @Test
    fun `대상 학생 조회가 실패해도 예외를 다시 던지지 않고 알림을 생성하지 않는다`() {
        given(programDiscordPayloadQueryPort.findById(1L)).willReturn(snapshot())
        given(notificationAudienceQueryPort.findEligibleStudentIds(emptySet())).willThrow(RuntimeException("db down"))

        listener.onProgramDiscordEvent(ProgramDiscordEvent(1L, ProgramDiscordAction.PUBLISHED))

        verifyNoInteractions(notificationService)
    }

    private fun commandFor(recipientMemberId: Long): NotificationCreateCommand =
        NotificationCreateCommand(
            recipientMemberId = recipientMemberId,
            type = NotificationType.PROGRAM_PUBLISHED,
            title = "새 프로그램이 게시되었습니다",
            content = "\"여름 캠프\" 프로그램이 게시되었습니다.",
            sourceEventType = "ProgramDiscordEvent:PUBLISHED",
            sourceEventId = 1L,
            targetType = NotificationTargetType.PROGRAM,
            targetId = 1L,
        )

    private fun captureCommands(expectedCount: Int): List<NotificationCreateCommand> {
        val captor = ArgumentCaptor.forClass(NotificationCreateCommand::class.java)
        verify(notificationService, times(expectedCount)).create(captor.capture() ?: commandFor(0L))
        return captor.allValues
    }
}
