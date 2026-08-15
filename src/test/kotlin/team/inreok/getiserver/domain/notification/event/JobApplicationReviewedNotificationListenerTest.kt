package team.inreok.getiserver.domain.notification.event

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import org.springframework.core.task.SyncTaskExecutor
import team.inreok.getiserver.domain.application.event.JobApplicationReviewedEvent
import team.inreok.getiserver.domain.notification.dto.NotificationCreateCommand
import team.inreok.getiserver.domain.notification.entity.type.NotificationTargetType
import team.inreok.getiserver.domain.notification.entity.type.NotificationType
import team.inreok.getiserver.domain.notification.service.NotificationService

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JobApplicationReviewedNotificationListenerTest {
    @Mock
    private lateinit var notificationService: NotificationService

    // 실행 Thread 분리 자체는 이 Test의 관심사가 아니라 동기 Executor를 넣어 호출 결과를 그대로
    // 검증한다. 실제 비동기 실행은 JobApplicationNotificationIntegrationTest가 확인한다
    // (MemberApprovalProcessedNotificationListenerTest와 동일한 이유).
    private val listener by lazy {
        JobApplicationReviewedNotificationListener(notificationService, SyncTaskExecutor())
    }

    private fun eventOf(
        action: String,
        reason: String? = null,
    ) = JobApplicationReviewedEvent(applicationId = 1L, studentMemberId = 42L, action = action, reason = reason)

    @Test
    fun `ALLOW_EDIT은 수정 허용 알림으로 지원자에게 생성된다`() {
        listener.onJobApplicationReviewed(eventOf("ALLOW_EDIT"))

        val command = captureCommand()
        assertThat(command.recipientMemberId).isEqualTo(42L)
        assertThat(command.type).isEqualTo(NotificationType.JOB_APPLICATION_STATUS_CHANGED)
        assertThat(command.targetType).isEqualTo(NotificationTargetType.JOB_APPLICATION)
        assertThat(command.targetId).isEqualTo(1L)
        assertThat(command.title).contains("수정")
    }

    @Test
    fun `REQUEST_REVISION은 사유를 본문에 담는다`() {
        listener.onJobApplicationReviewed(eventOf("REQUEST_REVISION", reason = "포트폴리오 링크를 추가해주세요."))

        val command = captureCommand()
        assertThat(command.title).contains("보완")
        assertThat(command.content).contains("포트폴리오 링크를 추가해주세요.")
    }

    @Test
    fun `APPROVE는 승인 알림으로 생성된다`() {
        listener.onJobApplicationReviewed(eventOf("APPROVE"))

        val command = captureCommand()
        assertThat(command.title).contains("승인")
        assertThat(command.content).isEqualTo("지원이 승인되었습니다.")
    }

    @Test
    fun `REJECT는 사유를 본문에 담는다`() {
        listener.onJobApplicationReviewed(eventOf("REJECT", reason = "정원이 마감되었습니다."))

        val command = captureCommand()
        assertThat(command.title).contains("거절")
        assertThat(command.content).contains("정원이 마감되었습니다.")
    }

    @Test
    fun `사유가 없어도 사유 없는 문구로 알림을 생성한다`() {
        listener.onJobApplicationReviewed(eventOf("REJECT"))

        val command = captureCommand()
        assertThat(command.content).isEqualTo("지원이 거절되었습니다.")
    }

    @Test
    fun `알림 생성이 실패해도 예외를 다시 던지지 않는다`() {
        given(notificationService.create(anyCommand())).willThrow(RuntimeException("db down"))

        listener.onJobApplicationReviewed(eventOf("APPROVE"))
    }

    private fun captureCommand(): NotificationCreateCommand {
        val captor = ArgumentCaptor.forClass(NotificationCreateCommand::class.java)
        // capture()가 반환하는 Java platform type을 non-null 파라미터에 그대로 넘기면 Kotlin이
        // 끼워 넣은 호출부 null 검사 때문에 NPE가 난다. Elvis 기본값으로 우회한다
        // (MemberApprovalProcessedNotificationListenerTest와 동일한 이유).
        verify(notificationService).create(captor.capture() ?: emptyCommand())
        return captor.value
    }

    private fun anyCommand(): NotificationCreateCommand = any(NotificationCreateCommand::class.java) ?: emptyCommand()

    private fun emptyCommand(): NotificationCreateCommand =
        NotificationCreateCommand(
            recipientMemberId = 0L,
            type = NotificationType.SYSTEM,
            title = "",
            content = "",
        )
}
