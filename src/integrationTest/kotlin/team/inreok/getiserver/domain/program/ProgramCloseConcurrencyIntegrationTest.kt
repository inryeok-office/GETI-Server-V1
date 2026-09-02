package team.inreok.getiserver.domain.program

import com.redis.testcontainers.RedisContainer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.times
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
import team.inreok.getiserver.domain.member.entity.type.OAuthProvider
import team.inreok.getiserver.domain.member.repository.MemberRepository
import team.inreok.getiserver.domain.notification.dto.DiscordDeliveryEnqueueCommand
import team.inreok.getiserver.domain.notification.entity.type.DiscordMessageTemplate
import team.inreok.getiserver.domain.notification.service.DiscordDeliveryService
import team.inreok.getiserver.domain.program.entity.Program
import team.inreok.getiserver.domain.program.entity.type.ProgramStatus
import team.inreok.getiserver.domain.program.entity.type.ProgramType
import team.inreok.getiserver.domain.program.repository.ProgramRepository
import team.inreok.getiserver.domain.program.service.ProgramCloseService
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * `ProgramCloseService.closeIfExpired`의 동시 마감 경쟁을 실제 PostgreSQL Pessimistic Lock으로
 * 검증한다(원본 요구사항 문서 8절, Phase 7). `ProgramApplicationConcurrencyIntegrationTest`와 같은
 * 이유로 Service Test(Mock Repository)는 `findByIdForUpdate`의 실제 Row Lock을 재현할 수 없어 이
 * Test에서만 확인된다.
 *
 * `ProgramCloseServiceImpl.closeIfExpired()`가 `ProgramRepository.findByIdForUpdate`로 Program
 * Row에 `PESSIMISTIC_WRITE` Lock을 건 뒤 상태를 확인·전이하므로, 같은 Program에 대한 동시 마감
 * 시도는 실제 DB 차원에서 순차적으로 처리되고 단 한 Thread만 실제로 CLOSED 전이를 완료한다.
 */
@Testcontainers
@SpringBootTest(
    // WebEnvironment.NONE은 Servlet Web Context를 만들지 않아 SecurityConfig의 HttpSecurity Bean을
    // 구성할 수 없다(ProgramApplicationConcurrencyIntegrationTest와 동일하게 MOCK을 사용, 실제 HTTP
    // 요청은 보내지 않고 Service Bean만 사용한다).
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = [
        "app.jwt.secret=program-close-concurrency-integration-test-only-jwt-secret-value",
        "app.jwt.access-token-expiration-seconds=1800",
        "app.jwt.refresh-token-expiration-seconds=1209600",
        // src/main/resources/application.yaml이 이 Classpath에서 우선하는데 app.file.storage는
        // Profile별 파일(application-local/prod.yaml)에만 있어(Secret과 환경별 값이라) 여기서
        // 채운다. 이 Test는 실제 Storage에 접속하지 않고 Bean 생성만 필요하다.
        "app.file.storage.bucket=geti-integration-test",
        "app.file.storage.region=us-east-1",
        "app.file.storage.access-key=integration-test-only-access-key",
        "app.file.storage.secret-key=integration-test-only-secret-key",
        // Discord PROGRAM_CLOSED 연동 검증에 필요한 허용 채널(Issue #97과 같은 방식).
        "app.discord.channel-policy.channels.program-close-concurrency-test.channel-id=program-close-concurrency-test-channel",
    ],
)
class ProgramCloseConcurrencyIntegrationTest {
    @Autowired
    private lateinit var programCloseService: ProgramCloseService

    @Autowired
    private lateinit var programRepository: ProgramRepository

    @Autowired
    private lateinit var memberRepository: MemberRepository

    // ProgramCloseSchedulerIntegrationTest와 같은 방식으로 Discord Delivery 생성 여부를 Event
    // 발행 횟수의 대리 지표로 삼는다 -- ProgramDiscordEventListener가 AFTER_COMMIT 시점에 동기로
    // enqueue를 호출하므로, enqueue 호출 횟수가 곧 논리적인 ProgramDiscordEvent(CLOSED) 발행 횟수다.
    @MockitoBean
    private lateinit var discordDeliveryService: DiscordDeliveryService

    @Test
    fun `신청 종료 시각이 지난 같은 Program에 여러 Thread가 동시에 마감을 시도하면 한 Thread만 성공한다`() {
        val teacherId = createMember("close-concurrency-teacher")
        val now = LocalDateTime.now()
        val programId =
            createProgram(
                teacherId,
                applicationEndedAt = now.minusDays(1),
                discordChannelId = "program-close-concurrency-test-channel",
            )

        val results = closeConcurrently(programId, now, threadCount = 5)

        assertThat(results.count { it }).isEqualTo(1)
        assertThat(statusOf(programId)).isEqualTo(ProgramStatus.CLOSED)
        // AFTER_COMMIT Listener는 실제 Commit이 일어난 Thread에서만 동기 실행되므로, closeIfExpired가
        // true를 반환한 단 한 Thread에서만 enqueue가 호출된다.
        verify(discordDeliveryService, times(1)).enqueue(anyCommand())
    }

    private fun closeConcurrently(
        programId: Long,
        now: LocalDateTime,
        threadCount: Int,
    ): List<Boolean> {
        val executor = Executors.newFixedThreadPool(threadCount)
        val ready = CountDownLatch(threadCount)
        val start = CountDownLatch(1)

        // executor.submit(Runnable)이 반환하는 Future를 버리면(원래 코드) Thread 안에서 던진
        // 예외가 조용히 삼켜진다 -- 5개 Thread가 전부 실패해도 Assertion은 그냥
        // "expected 1 but was 0"만 보여줘 원인을 알 수 없다(GETI-Server-V1 CI에서 실제로 관측된
        // 산발적 실패, Issue #260 조사 중 발견). Future<Boolean>으로 받아 .get()을 호출해
        // 실제 예외가 Test 실패에 그대로 드러나게 한다.
        val futures =
            (1..threadCount).map {
                executor.submit<Boolean> {
                    ready.countDown()
                    start.await()
                    programCloseService.closeIfExpired(programId, now)
                }
            }
        ready.await()
        start.countDown()
        executor.shutdown()
        check(executor.awaitTermination(30, TimeUnit.SECONDS)) { "동시 마감 Thread가 제한 시간 안에 끝나지 않았습니다." }
        return futures.map { it.get() }
    }

    private fun statusOf(programId: Long): ProgramStatus = programRepository.findById(programId).orElseThrow().status

    private fun anyCommand(): DiscordDeliveryEnqueueCommand =
        any() ?: DiscordDeliveryEnqueueCommand(DiscordMessageTemplate.PROGRAM_CLOSED, 0L, "")

    private fun createMember(subject: String): Long {
        val member =
            memberRepository.saveAndFlush(
                Member(
                    oauthProvider = OAuthProvider.DG,
                    oauthSubject = subject,
                    email = "$subject@example.com",
                ),
            )
        return requireNotNull(member.id)
    }

    private fun createProgram(
        createdByMemberId: Long,
        applicationEndedAt: LocalDateTime?,
        discordChannelId: String? = null,
    ): Long {
        val program =
            programRepository.saveAndFlush(
                Program(
                    createdByMemberId = createdByMemberId,
                    type = ProgramType.SPECIAL_LECTURE,
                    title = "동시 마감 Test 특강",
                    status = ProgramStatus.PUBLISHED,
                ).apply {
                    this.applicationEndedAt = applicationEndedAt
                    applicationStartedAt = applicationEndedAt?.minusDays(7)
                    this.discordChannelId = discordChannelId
                },
            )
        return requireNotNull(program.id)
    }

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
