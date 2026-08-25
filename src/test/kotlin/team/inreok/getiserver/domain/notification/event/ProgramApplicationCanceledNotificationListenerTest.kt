package team.inreok.getiserver.domain.notification.event

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import org.springframework.core.task.SyncTaskExecutor
import team.inreok.getiserver.domain.notification.dto.NotificationCreateCommand
import team.inreok.getiserver.domain.notification.entity.type.NotificationTargetType
import team.inreok.getiserver.domain.notification.entity.type.NotificationType
import team.inreok.getiserver.domain.notification.service.NotificationService
import team.inreok.getiserver.domain.program.event.ProgramApplicationCanceledEvent
import team.inreok.getiserver.domain.program.query.ProgramManagerQueryPort
import team.inreok.getiserver.domain.program.query.ProgramManagerSnapshot

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProgramApplicationCanceledNotificationListenerTest {
    @Mock
    private lateinit var programManagerQueryPort: ProgramManagerQueryPort

    @Mock
    private lateinit var notificationService: NotificationService

    private val listener by lazy {
        ProgramApplicationCanceledNotificationListener(programManagerQueryPort, notificationService, SyncTaskExecutor())
    }

    private val event =
        ProgramApplicationCanceledEvent(
            programId = 1L,
            applicationId = 100L,
            applicantMemberId = 10L,
            programTitle = "여름 캠프",
        )

    @Test
    fun `학생 본인과 담당 교사 모두에게 알림을 생성한다`() {
        given(programManagerQueryPort.findById(1L))
            .willReturn(ProgramManagerSnapshot(programId = 1L, createdByMemberId = 20L, managerMemberId = 20L))

        listener.onProgramApplicationCanceled(event)

        val commands = captureCommands(2)
        assertThat(commands.map { it.recipientMemberId }).containsExactlyInAnyOrder(10L, 20L)
        assertThat(commands).allSatisfy { command ->
            assertThat(command.type).isEqualTo(NotificationType.PROGRAM_APPLICATION_CANCELED)
            assertThat(command.targetType).isEqualTo(NotificationTargetType.PROGRAM)
            assertThat(command.targetId).isEqualTo(1L)
            assertThat(command.content).contains("여름 캠프")
            assertThat(command.sourceEventType).isEqualTo("ProgramApplicationCanceledEvent")
            assertThat(command.sourceEventId).isEqualTo(100L)
        }
    }

    @Test
    fun `담당 교사가 없으면 학생 본인에게만 알림을 생성한다`() {
        given(programManagerQueryPort.findById(1L))
            .willReturn(ProgramManagerSnapshot(programId = 1L, createdByMemberId = 20L, managerMemberId = null))

        listener.onProgramApplicationCanceled(event)

        assertThat(captureCommands(1).single().recipientMemberId).isEqualTo(10L)
    }

    @Test
    fun `학생 알림 생성이 실패해도 교사 알림은 생성된다`() {
        given(programManagerQueryPort.findById(1L))
            .willReturn(ProgramManagerSnapshot(programId = 1L, createdByMemberId = 20L, managerMemberId = 20L))
        given(notificationService.create(studentCommand())).willThrow(RuntimeException("db down"))

        listener.onProgramApplicationCanceled(event)

        verify(notificationService).create(managerCommand())
    }

    private fun studentCommand(): NotificationCreateCommand =
        NotificationCreateCommand(
            recipientMemberId = 10L,
            type = NotificationType.PROGRAM_APPLICATION_CANCELED,
            title = "프로그램 신청이 취소되었습니다",
            content = "\"여름 캠프\" 프로그램 신청이 취소되었습니다.",
            sourceEventType = "ProgramApplicationCanceledEvent",
            sourceEventId = 100L,
            targetType = NotificationTargetType.PROGRAM,
            targetId = 1L,
        )

    private fun managerCommand(): NotificationCreateCommand =
        NotificationCreateCommand(
            recipientMemberId = 20L,
            type = NotificationType.PROGRAM_APPLICATION_CANCELED,
            title = "프로그램 신청이 취소되었습니다",
            content = "\"여름 캠프\" 프로그램에 신청했던 학생이 신청을 취소했습니다.",
            sourceEventType = "ProgramApplicationCanceledEvent",
            sourceEventId = 100L,
            targetType = NotificationTargetType.PROGRAM,
            targetId = 1L,
        )

    private fun captureCommands(expectedCount: Int): List<NotificationCreateCommand> {
        val captor = ArgumentCaptor.forClass(NotificationCreateCommand::class.java)
        verify(notificationService, times(expectedCount)).create(captor.capture() ?: studentCommand())
        return captor.allValues
    }
}
