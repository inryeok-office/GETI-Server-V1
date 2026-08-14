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
import team.inreok.getiserver.domain.notification.dto.NotificationCreateCommand
import team.inreok.getiserver.domain.notification.entity.type.NotificationTargetType
import team.inreok.getiserver.domain.notification.entity.type.NotificationType
import team.inreok.getiserver.domain.notification.service.NotificationService
import team.inreok.getiserver.domain.program.event.ProgramDeletedEvent
import team.inreok.getiserver.domain.program.query.ProgramApplicantQueryPort

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProgramDeletedNotificationListenerTest {
    @Mock
    private lateinit var programApplicantQueryPort: ProgramApplicantQueryPort

    @Mock
    private lateinit var notificationService: NotificationService

    // 실행 Thread 분리 자체는 이 Test의 관심사가 아니라 동기 Executor를 넣어 호출 결과를 그대로
    // 검증한다. 실제 비동기 실행은 DomainEventNotificationIntegrationTest가 확인한다.
    private val listener by lazy {
        ProgramDeletedNotificationListener(programApplicantQueryPort, notificationService, SyncTaskExecutor())
    }

    private val event = ProgramDeletedEvent(programId = 1L, title = "여름 캠프")

    @Test
    fun `활성 신청자 모두에게 PROGRAM_DELETED 알림을 생성한다`() {
        given(programApplicantQueryPort.findActiveApplicantMemberIds(1L)).willReturn(listOf(10L, 11L))

        listener.onProgramDeleted(event)

        val commands = captureCommands(2)
        assertThat(commands.map { it.recipientMemberId }).containsExactly(10L, 11L)
        assertThat(commands).allSatisfy { command ->
            assertThat(command.type).isEqualTo(NotificationType.PROGRAM_DELETED)
            assertThat(command.targetType).isEqualTo(NotificationTargetType.PROGRAM)
            assertThat(command.targetId).isEqualTo(1L)
            assertThat(command.content).contains("여름 캠프")
        }
    }

    @Test
    fun `신청자가 없으면 알림을 생성하지 않는다`() {
        given(programApplicantQueryPort.findActiveApplicantMemberIds(1L)).willReturn(emptyList())

        listener.onProgramDeleted(event)

        verifyNoInteractions(notificationService)
    }

    @Test
    fun `한 수신자의 알림 생성이 실패해도 나머지 수신자에게는 알림을 생성한다`() {
        given(programApplicantQueryPort.findActiveApplicantMemberIds(1L)).willReturn(listOf(10L, 11L))
        given(notificationService.create(commandFor(10L))).willThrow(RuntimeException("db down"))

        listener.onProgramDeleted(event)

        verify(notificationService).create(commandFor(11L))
    }

    @Test
    fun `신청자 조회가 실패해도 예외를 다시 던지지 않고 알림을 생성하지 않는다`() {
        given(programApplicantQueryPort.findActiveApplicantMemberIds(1L)).willThrow(RuntimeException("db down"))

        listener.onProgramDeleted(event)

        verifyNoInteractions(notificationService)
    }

    private fun commandFor(recipientMemberId: Long): NotificationCreateCommand =
        NotificationCreateCommand(
            recipientMemberId = recipientMemberId,
            type = NotificationType.PROGRAM_DELETED,
            title = "신청한 프로그램이 삭제되었습니다",
            content = "\"여름 캠프\" 프로그램이 삭제되어 신청이 더 이상 유효하지 않습니다.",
            targetType = NotificationTargetType.PROGRAM,
            targetId = 1L,
        )

    private fun captureCommands(expectedCount: Int): List<NotificationCreateCommand> {
        val captor = ArgumentCaptor.forClass(NotificationCreateCommand::class.java)
        // capture()가 반환하는 Java platform type을 non-null 파라미터에 그대로 넘기면 Kotlin이
        // 끼워 넣은 호출부 null 검사 때문에 NPE가 난다. Elvis 기본값으로 우회한다
        // (ProgramDiscordEventListenerTest와 동일한 이유).
        verify(notificationService, times(expectedCount)).create(captor.capture() ?: commandFor(0L))
        return captor.allValues
    }
}
