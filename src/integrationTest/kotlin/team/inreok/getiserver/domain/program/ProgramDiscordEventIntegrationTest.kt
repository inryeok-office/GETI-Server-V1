package team.inreok.getiserver.domain.program

import com.redis.testcontainers.RedisContainer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito.given
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import team.inreok.getiserver.domain.member.entity.Member
import team.inreok.getiserver.domain.member.entity.type.MemberStatus
import team.inreok.getiserver.domain.member.entity.type.OAuthProvider
import team.inreok.getiserver.domain.member.repository.MemberRepository
import team.inreok.getiserver.domain.notification.dto.DiscordDeliveryEnqueueCommand
import team.inreok.getiserver.domain.notification.entity.type.DiscordMessageTemplate
import team.inreok.getiserver.domain.notification.service.DiscordDeliveryService
import team.inreok.getiserver.domain.program.dto.ProgramCreateRequest
import team.inreok.getiserver.domain.program.entity.type.ProgramStatus
import team.inreok.getiserver.domain.program.entity.type.ProgramType
import team.inreok.getiserver.domain.program.service.ProgramService
import java.time.LocalDateTime
import java.util.UUID

/**
 * `ProgramDiscordEventListener`(`domain.notification`)가 실제 PostgreSQL Commit 경계를 사이에
 * 두고 의도대로 동작하는지 검증한다(Issue #97). [team.inreok.getiserver.domain.job.JobDiscordEventIntegrationTest]와
 * 같은 방식이다.
 */
@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = [
        "app.jwt.secret=program-discord-event-integration-test-only-jwt-secret-value",
        "app.jwt.access-token-expiration-seconds=1800",
        "app.jwt.refresh-token-expiration-seconds=1209600",
        "app.file.storage.bucket=geti-integration-test",
        "app.file.storage.region=us-east-1",
        "app.file.storage.access-key=integration-test-only-access-key",
        "app.file.storage.secret-key=integration-test-only-secret-key",
        // Program 등록이 discordChannelId를 허용 목록에서 검증한다(Issue #97).
        "app.discord.channel-policy.channels.program-discord-event-test.channel-id=program-discord-event-test-channel",
    ],
)
class ProgramDiscordEventIntegrationTest {
    @Autowired
    private lateinit var programService: ProgramService

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @MockitoBean
    private lateinit var discordDeliveryService: DiscordDeliveryService

    // Kotlin non-null 파라미터에 bare any()를 쓰면 null 반환으로 NPE가 나므로 Elvis로 기본값을
    // 채운다.
    private fun anyCommand(): DiscordDeliveryEnqueueCommand =
        any(DiscordDeliveryEnqueueCommand::class.java)
            ?: DiscordDeliveryEnqueueCommand(DiscordMessageTemplate.PROGRAM_PUBLISHED, 0L, "")

    private fun captureCommand(captor: ArgumentCaptor<DiscordDeliveryEnqueueCommand>): DiscordDeliveryEnqueueCommand =
        captor.capture() ?: DiscordDeliveryEnqueueCommand(DiscordMessageTemplate.PROGRAM_PUBLISHED, 0L, "")

    @Test
    fun `Discord 예약이 실패해도 프로그램 등록 Transaction은 이미 Commit되어 영향을 받지 않는다`() {
        given(discordDeliveryService.enqueue(anyCommand())).willThrow(RuntimeException("Discord 예약 강제 실패(Test 전용)"))

        val teacherId = requireNotNull(createMember("program-discord-event-teacher").id)
        val now = LocalDateTime.now()

        // create는 @Transactional Method라 반환 시점에는 이미 Commit이 끝나 있고, AFTER_COMMIT
        // Listener(ProgramDiscordEventListener)도 같은 호출 Thread 안에서 동기 실행을 마친 뒤다.
        val response =
            programService.create(
                ProgramCreateRequest(
                    title = "Discord Event 연동 Test용 특강",
                    programType = ProgramType.SPECIAL_LECTURE,
                    status = ProgramStatus.PUBLISHED,
                    content = "본문",
                    startAt = now.plusDays(3),
                    endAt = now.plusDays(3).plusHours(2),
                    applicationStartAt = now.minusDays(1),
                    applicationEndAt = now.plusDays(2),
                    location = "본관 대강당",
                    targetGrades = listOf(1, 2, 3),
                    capacity = 20,
                    discordChannelId = "program-discord-event-test-channel",
                ),
                teacherId,
            )

        assertThat(response.status).isEqualTo(ProgramStatus.PUBLISHED)
        assertThat(response.programId).isNotNull()

        // Listener가 실제로 실행됐는지 확인한다. 이 검증이 없으면 Listener가 아예 동작하지
        // 않아도(Event 미발행, Bean 누락, 채널 해석 실패) 위 단언만으로는 똑같이 통과해
        // 이 Test의 관심사인 "예외가 원본 Transaction으로 번지지 않는다"를 검증할 수 없다.
        val captor = ArgumentCaptor.forClass(DiscordDeliveryEnqueueCommand::class.java)
        verify(discordDeliveryService).enqueue(captureCommand(captor))
        assertThat(captor.value.template).isEqualTo(DiscordMessageTemplate.PROGRAM_PUBLISHED)
        assertThat(captor.value.targetId).isEqualTo(response.programId)
        assertThat(captor.value.channelId).isEqualTo("program-discord-event-test-channel")
    }

    private fun createMember(subject: String): Member =
        memberRepository.saveAndFlush(
            Member(
                oauthProvider = OAuthProvider.DG,
                oauthSubject = "$subject-${UUID.randomUUID()}",
                email = "$subject-${UUID.randomUUID()}@example.com",
                status = MemberStatus.ACTIVE,
            ).apply { name = subject },
        )

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:18.4-alpine"))

        @Container
        @ServiceConnection
        @JvmStatic
        val redis = RedisContainer(DockerImageName.parse("redis:8.8.1-alpine"))
    }
}
