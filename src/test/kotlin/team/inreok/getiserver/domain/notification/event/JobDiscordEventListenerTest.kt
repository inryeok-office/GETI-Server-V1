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
import team.inreok.getiserver.domain.job.event.JobDiscordAction
import team.inreok.getiserver.domain.job.event.JobDiscordEvent
import team.inreok.getiserver.domain.job.query.JobDiscordPayloadQueryPort
import team.inreok.getiserver.domain.job.query.JobDiscordPayloadSnapshot
import team.inreok.getiserver.domain.notification.dto.DiscordDeliveryEnqueueCommand
import team.inreok.getiserver.domain.notification.entity.type.DiscordMessageTemplate
import team.inreok.getiserver.domain.notification.service.DiscordDeliveryService
import team.inreok.getiserver.global.discord.DiscordChannelResolver
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JobDiscordEventListenerTest {
    @Mock
    private lateinit var jobDiscordPayloadQueryPort: JobDiscordPayloadQueryPort

    @Mock
    private lateinit var discordChannelResolver: DiscordChannelResolver

    @Mock
    private lateinit var discordDeliveryService: DiscordDeliveryService

    private val listener by lazy {
        JobDiscordEventListener(jobDiscordPayloadQueryPort, discordChannelResolver, discordDeliveryService)
    }

    private val updatedAt = LocalDateTime.of(2026, 8, 10, 9, 0)

    @Test
    fun `PUBLISHED는 JOB_PUBLISHED Template과 학년 Role로 예약한다`() {
        given(
            jobDiscordPayloadQueryPort.findById(1L),
        ).willReturn(snapshot(discordChannelKey = "job-notice", targetGrade = 3))
        given(discordChannelResolver.resolveJobChannelId("job-notice")).willReturn("channel-1")
        given(discordChannelResolver.roleIdsForGrades(listOf(3))).willReturn(listOf("role-3"))

        listener.onJobDiscordEvent(JobDiscordEvent(1L, JobDiscordAction.PUBLISHED))

        val command = captureCommand()
        assertThat(command.template).isEqualTo(DiscordMessageTemplate.JOB_PUBLISHED)
        assertThat(command.targetId).isEqualTo(1L)
        assertThat(command.channelId).isEqualTo("channel-1")
        assertThat(command.roleIds).containsExactly("role-3")
        assertThat(command.sourceUpdatedAt).isNull()
    }

    @Test
    fun `학년 제한이 없으면 빈 Role 목록으로 예약한다`() {
        // roleIdsForGrades를 stub하지 않으면 Mockito 기본 응답인 빈 List가 그대로 쓰인다.
        given(jobDiscordPayloadQueryPort.findById(1L)).willReturn(snapshot(targetGrade = null))
        given(discordChannelResolver.resolveJobChannelId("job-notice")).willReturn("channel-1")

        listener.onJobDiscordEvent(JobDiscordEvent(1L, JobDiscordAction.PUBLISHED))

        assertThat(captureCommand().roleIds).isEmpty()
    }

    @Test
    fun `UPDATED는 원본 수정 시각을 함께 전달하고 Mention하지 않는다`() {
        given(jobDiscordPayloadQueryPort.findById(1L)).willReturn(snapshot(updatedAt = updatedAt))
        given(discordChannelResolver.resolveJobChannelId("job-notice")).willReturn("channel-1")

        listener.onJobDiscordEvent(JobDiscordEvent(1L, JobDiscordAction.UPDATED))

        val command = captureCommand()
        assertThat(command.template).isEqualTo(DiscordMessageTemplate.JOB_UPDATED)
        assertThat(command.sourceUpdatedAt).isEqualTo(updatedAt)
        assertThat(command.roleIds).isEmpty()
    }

    @Test
    fun `CLOSED는 JOB_CLOSED Template로 예약한다`() {
        given(jobDiscordPayloadQueryPort.findById(1L)).willReturn(snapshot())
        given(discordChannelResolver.resolveJobChannelId("job-notice")).willReturn("channel-1")

        listener.onJobDiscordEvent(JobDiscordEvent(1L, JobDiscordAction.CLOSED))

        assertThat(captureCommand().template).isEqualTo(DiscordMessageTemplate.JOB_CLOSED)
    }

    @Test
    fun `DELETED는 JOB_DELETED Template로 예약한다`() {
        given(jobDiscordPayloadQueryPort.findById(1L)).willReturn(snapshot())
        given(discordChannelResolver.resolveJobChannelId("job-notice")).willReturn("channel-1")

        listener.onJobDiscordEvent(JobDiscordEvent(1L, JobDiscordAction.DELETED))

        assertThat(captureCommand().template).isEqualTo(DiscordMessageTemplate.JOB_DELETED)
    }

    @Test
    fun `대상을 찾을 수 없으면 예약하지 않는다`() {
        given(jobDiscordPayloadQueryPort.findById(1L)).willReturn(null)

        listener.onJobDiscordEvent(JobDiscordEvent(1L, JobDiscordAction.PUBLISHED))

        verifyNoInteractions(discordDeliveryService)
    }

    @Test
    fun `채널이 설정되지 않았으면 예약하지 않는다`() {
        // resolveJobChannelId를 stub하지 않으면 Mockito 기본 응답인 null이 그대로 쓰인다
        // (Fail-Fast 아님, §5.1).
        given(jobDiscordPayloadQueryPort.findById(1L)).willReturn(snapshot())

        listener.onJobDiscordEvent(JobDiscordEvent(1L, JobDiscordAction.PUBLISHED))

        verifyNoInteractions(discordDeliveryService)
    }

    @Test
    fun `예약이 실패해도 예외를 다시 던지지 않는다`() {
        given(jobDiscordPayloadQueryPort.findById(1L)).willReturn(snapshot())
        given(discordChannelResolver.resolveJobChannelId("job-notice")).willReturn("channel-1")
        // 어차피 인자가 하나뿐이라 와일드카드 Matcher 없이, 이 stub 조합이 만들 것으로 예상되는
        // Command와 정확히 같은 값으로 stub한다.
        given(
            discordDeliveryService.enqueue(
                DiscordDeliveryEnqueueCommand(DiscordMessageTemplate.JOB_PUBLISHED, 1L, "channel-1"),
            ),
        ).willThrow(RuntimeException("db down"))

        listener.onJobDiscordEvent(JobDiscordEvent(1L, JobDiscordAction.PUBLISHED))
    }

    private fun captureCommand(): DiscordDeliveryEnqueueCommand {
        val captor = ArgumentCaptor.forClass(DiscordDeliveryEnqueueCommand::class.java)
        // capture()는 Java Generic이라 Kotlin이 반환값을 platform type으로 본다. enqueue()의
        // 파라미터가 non-null이라 Kotlin이 호출부에 null 검사를 끼워 넣어 그대로 쓰면 NPE가
        // 난다(DiscordDeliveryServiceImplTest의 any() 사례와 같은 원인). Elvis 기본값으로 우회한다.
        verify(discordDeliveryService).enqueue(
            captor.capture() ?: DiscordDeliveryEnqueueCommand(DiscordMessageTemplate.JOB_PUBLISHED, 0L, ""),
        )
        return captor.value
    }

    private fun snapshot(
        discordChannelKey: String? = "job-notice",
        targetGrade: Int? = null,
        updatedAt: LocalDateTime = this.updatedAt,
    ) = JobDiscordPayloadSnapshot(
        jobId = 1L,
        title = "백엔드 신입 채용",
        companyId = 1L,
        companyName = "인력개발원",
        recruitmentEndedAt = null,
        discordChannelKey = discordChannelKey,
        targetGrade = targetGrade,
        updatedAt = updatedAt,
    )
}
