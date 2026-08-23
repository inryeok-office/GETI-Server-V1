package team.inreok.getiserver.domain.notification.event

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import org.springframework.core.task.SyncTaskExecutor
import team.inreok.getiserver.domain.member.event.MemberApprovalProcessedEvent
import team.inreok.getiserver.domain.notification.dto.NotificationCreateCommand
import team.inreok.getiserver.domain.notification.entity.type.NotificationTargetType
import team.inreok.getiserver.domain.notification.entity.type.NotificationType
import team.inreok.getiserver.domain.notification.service.NotificationService

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MemberApprovalProcessedNotificationListenerTest {
    @Mock
    private lateinit var notificationService: NotificationService

    // 실행 Thread 분리 자체는 이 Test의 관심사가 아니라 동기 Executor를 넣어 호출 결과를 그대로
    // 검증한다. 실제 비동기 실행은 DomainEventNotificationIntegrationTest가 확인한다.
    private val listener by lazy {
        MemberApprovalProcessedNotificationListener(notificationService, SyncTaskExecutor())
    }

    @Test
    fun `승인 결과는 MEMBER_APPROVAL_RESULT 알림으로 대상 회원에게 생성된다`() {
        listener.onMemberApprovalProcessed(
            MemberApprovalProcessedEvent(memberId = 42L, approved = true, rejectionReason = null),
        )

        val command = captureCommand()
        assertThat(command.recipientMemberId).isEqualTo(42L)
        assertThat(command.type).isEqualTo(NotificationType.MEMBER_APPROVAL_RESULT)
        assertThat(command.targetType).isEqualTo(NotificationTargetType.MEMBER_APPROVAL)
        assertThat(command.targetId).isEqualTo(42L)
        assertThat(command.title).contains("승인")
        assertThat(command.content).contains("승인")
        assertThat(command.sourceEventType).isEqualTo("MemberApprovalProcessedEvent")
        assertThat(command.sourceEventId).isEqualTo(42L)
    }

    @Test
    fun `거절 결과는 거절 사유를 본문에 담는다`() {
        listener.onMemberApprovalProcessed(
            MemberApprovalProcessedEvent(memberId = 42L, approved = false, rejectionReason = "재직 증명 자료 미확인"),
        )

        val command = captureCommand()
        assertThat(command.type).isEqualTo(NotificationType.MEMBER_APPROVAL_RESULT)
        assertThat(command.title).contains("거절")
        assertThat(command.content).contains("재직 증명 자료 미확인")
    }

    @Test
    fun `거절 사유가 없어도 사유 없는 문구로 알림을 생성한다`() {
        listener.onMemberApprovalProcessed(
            MemberApprovalProcessedEvent(memberId = 42L, approved = false, rejectionReason = null),
        )

        val command = captureCommand()
        assertThat(command.content).isEqualTo("교직원 가입 신청이 거절되었습니다.")
    }

    @Test
    fun `알림 생성이 실패해도 예외를 다시 던지지 않는다`() {
        given(notificationService.create(anyCommand())).willThrow(RuntimeException("db down"))

        listener.onMemberApprovalProcessed(
            MemberApprovalProcessedEvent(memberId = 42L, approved = true, rejectionReason = null),
        )
    }

    private fun captureCommand(): NotificationCreateCommand {
        val captor = ArgumentCaptor.forClass(NotificationCreateCommand::class.java)
        // capture()가 반환하는 Java platform type을 non-null 파라미터에 그대로 넘기면 Kotlin이
        // 끼워 넣은 호출부 null 검사 때문에 NPE가 난다. Elvis 기본값으로 우회한다
        // (ProgramDiscordEventListenerTest와 동일한 이유).
        verify(notificationService).create(captor.capture() ?: emptyCommand())
        return captor.value
    }

    private fun anyCommand(): NotificationCreateCommand =
        org.mockito.ArgumentMatchers.any(NotificationCreateCommand::class.java) ?: emptyCommand()

    private fun emptyCommand(): NotificationCreateCommand =
        NotificationCreateCommand(
            recipientMemberId = 0L,
            type = NotificationType.SYSTEM,
            title = "",
            content = "",
            sourceEventType = "",
            sourceEventId = 0L,
        )
}
