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
import team.inreok.getiserver.domain.job.event.JobDiscordAction
import team.inreok.getiserver.domain.job.event.JobDiscordEvent
import team.inreok.getiserver.domain.job.query.JobDiscordPayloadQueryPort
import team.inreok.getiserver.domain.job.query.JobDiscordPayloadSnapshot
import team.inreok.getiserver.domain.member.query.NotificationAudienceQueryPort
import team.inreok.getiserver.domain.notification.dto.NotificationCreateCommand
import team.inreok.getiserver.domain.notification.entity.type.NotificationTargetType
import team.inreok.getiserver.domain.notification.entity.type.NotificationType
import team.inreok.getiserver.domain.notification.service.NotificationService
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JobPublishedNotificationListenerTest {
    @Mock
    private lateinit var jobDiscordPayloadQueryPort: JobDiscordPayloadQueryPort

    @Mock
    private lateinit var notificationAudienceQueryPort: NotificationAudienceQueryPort

    @Mock
    private lateinit var notificationService: NotificationService

    // 실행 Thread 분리 자체는 이 Test의 관심사가 아니라 동기 Executor를 넣어 호출 결과를 그대로
    // 검증한다(ProgramDeletedNotificationListenerTest와 동일한 이유).
    private val listener by lazy {
        JobPublishedNotificationListener(
            jobDiscordPayloadQueryPort,
            notificationAudienceQueryPort,
            notificationService,
            SyncTaskExecutor(),
        )
    }

    private fun snapshot(targetGrade: Int? = null) =
        JobDiscordPayloadSnapshot(
            jobId = 1L,
            title = "백엔드 신입 채용",
            companyId = 1L,
            companyName = "인력개발원",
            recruitmentEndedAt = null,
            discordChannelKey = "job-notice",
            targetGrade = targetGrade,
            updatedAt = LocalDateTime.of(2026, 8, 10, 9, 0),
        )

    @Test
    fun `PUBLISHED가 아니면 알림을 생성하지 않는다`() {
        listener.onJobDiscordEvent(JobDiscordEvent(1L, JobDiscordAction.UPDATED))

        verifyNoInteractions(jobDiscordPayloadQueryPort, notificationAudienceQueryPort, notificationService)
    }

    @Test
    fun `대상 학년이 있으면 그 학년의 대상 학생 모두에게 알림을 생성한다`() {
        given(jobDiscordPayloadQueryPort.findById(1L)).willReturn(snapshot(targetGrade = 3))
        given(notificationAudienceQueryPort.findEligibleStudentIds(setOf(3))).willReturn(listOf(10L, 11L))

        listener.onJobDiscordEvent(JobDiscordEvent(1L, JobDiscordAction.PUBLISHED))

        val commands = captureCommands(2)
        assertThat(commands.map { it.recipientMemberId }).containsExactly(10L, 11L)
        assertThat(commands).allSatisfy { command ->
            assertThat(command.type).isEqualTo(NotificationType.JOB_PUBLISHED)
            assertThat(command.targetType).isEqualTo(NotificationTargetType.JOB)
            assertThat(command.targetId).isEqualTo(1L)
            assertThat(command.content).contains("백엔드 신입 채용")
            assertThat(command.sourceEventType).isEqualTo("JobDiscordEvent:PUBLISHED")
            assertThat(command.sourceEventId).isEqualTo(1L)
        }
    }

    @Test
    fun `대상 학년이 없으면 전 학년 조건으로 조회한다`() {
        given(jobDiscordPayloadQueryPort.findById(1L)).willReturn(snapshot(targetGrade = null))
        given(notificationAudienceQueryPort.findEligibleStudentIds(emptySet())).willReturn(listOf(20L))

        listener.onJobDiscordEvent(JobDiscordEvent(1L, JobDiscordAction.PUBLISHED))

        assertThat(captureCommands(1).single().recipientMemberId).isEqualTo(20L)
    }

    @Test
    fun `대상을 찾을 수 없으면 알림을 생성하지 않는다`() {
        given(jobDiscordPayloadQueryPort.findById(1L)).willReturn(null)

        listener.onJobDiscordEvent(JobDiscordEvent(1L, JobDiscordAction.PUBLISHED))

        verifyNoInteractions(notificationAudienceQueryPort, notificationService)
    }

    @Test
    fun `대상 학생이 없으면 알림을 생성하지 않는다`() {
        given(jobDiscordPayloadQueryPort.findById(1L)).willReturn(snapshot())
        given(notificationAudienceQueryPort.findEligibleStudentIds(emptySet())).willReturn(emptyList())

        listener.onJobDiscordEvent(JobDiscordEvent(1L, JobDiscordAction.PUBLISHED))

        verifyNoInteractions(notificationService)
    }

    @Test
    fun `한 수신자의 알림 생성이 실패해도 나머지 수신자에게는 알림을 생성한다`() {
        given(jobDiscordPayloadQueryPort.findById(1L)).willReturn(snapshot())
        given(notificationAudienceQueryPort.findEligibleStudentIds(emptySet())).willReturn(listOf(10L, 11L))
        given(notificationService.create(commandFor(10L))).willThrow(RuntimeException("db down"))

        listener.onJobDiscordEvent(JobDiscordEvent(1L, JobDiscordAction.PUBLISHED))

        verify(notificationService).create(commandFor(11L))
    }

    @Test
    fun `대상 학생 조회가 실패해도 예외를 다시 던지지 않고 알림을 생성하지 않는다`() {
        given(jobDiscordPayloadQueryPort.findById(1L)).willReturn(snapshot())
        given(notificationAudienceQueryPort.findEligibleStudentIds(emptySet())).willThrow(RuntimeException("db down"))

        listener.onJobDiscordEvent(JobDiscordEvent(1L, JobDiscordAction.PUBLISHED))

        verifyNoInteractions(notificationService)
    }

    private fun commandFor(recipientMemberId: Long): NotificationCreateCommand =
        NotificationCreateCommand(
            recipientMemberId = recipientMemberId,
            type = NotificationType.JOB_PUBLISHED,
            title = "새 채용 공고가 게시되었습니다",
            content = "\"백엔드 신입 채용\" 공고가 게시되었습니다.",
            sourceEventType = "JobDiscordEvent:PUBLISHED",
            sourceEventId = 1L,
            targetType = NotificationTargetType.JOB,
            targetId = 1L,
        )

    private fun captureCommands(expectedCount: Int): List<NotificationCreateCommand> {
        val captor = ArgumentCaptor.forClass(NotificationCreateCommand::class.java)
        verify(notificationService, times(expectedCount)).create(captor.capture() ?: commandFor(0L))
        return captor.allValues
    }
}
