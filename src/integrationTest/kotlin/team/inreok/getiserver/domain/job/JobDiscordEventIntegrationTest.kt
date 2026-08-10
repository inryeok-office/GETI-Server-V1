package team.inreok.getiserver.domain.job

import com.redis.testcontainers.RedisContainer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import team.inreok.getiserver.domain.company.entity.Company
import team.inreok.getiserver.domain.company.entity.type.CompanyType
import team.inreok.getiserver.domain.company.repository.CompanyRepository
import team.inreok.getiserver.domain.job.dto.JobCreateRequest
import team.inreok.getiserver.domain.job.entity.type.ApplicationMethod
import team.inreok.getiserver.domain.job.entity.type.JobStatus
import team.inreok.getiserver.domain.job.entity.type.PostingType
import team.inreok.getiserver.domain.job.service.JobService
import team.inreok.getiserver.domain.member.entity.Member
import team.inreok.getiserver.domain.member.entity.type.MemberStatus
import team.inreok.getiserver.domain.member.entity.type.OAuthProvider
import team.inreok.getiserver.domain.member.repository.MemberRepository
import team.inreok.getiserver.domain.notification.dto.DiscordDeliveryEnqueueCommand
import team.inreok.getiserver.domain.notification.entity.type.DiscordMessageTemplate
import team.inreok.getiserver.domain.notification.service.DiscordDeliveryService
import java.util.UUID

/**
 * `JobDiscordEventListener`(`domain.notification`)가 실제 PostgreSQL Commit 경계를 사이에 두고
 * 의도대로 동작하는지 검증한다(Issue #97). `@TransactionalEventListener(phase = AFTER_COMMIT)`가
 * 원본 Transaction과 실제로 분리돼 있는지는 Mock Repository만으로는 확인할 수 없다.
 *
 * `InquiryAnswerNotificationFailureIntegrationTest`와 같은 방식(실제 PostgreSQL, 실패를
 * 강제하는 Mock)을 따른다. 채널은 `app.discord.channel-policy`에 등록해 Listener가 실제로
 * Delivery를 예약하게 만든다 -- 비워 두면 §5.1에 따라 조용히 건너뛰어 이 Test의 관심사(Commit
 * 경계)를 검증할 수 없다.
 */
@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = [
        "app.jwt.secret=job-discord-event-integration-test-only-jwt-secret-value",
        "app.jwt.access-token-expiration-seconds=1800",
        "app.jwt.refresh-token-expiration-seconds=1209600",
        "app.file.storage.bucket=geti-integration-test",
        "app.file.storage.region=us-east-1",
        "app.file.storage.access-key=integration-test-only-access-key",
        "app.file.storage.secret-key=integration-test-only-secret-key",
        "app.discord.channel-policy.channels.job-notice.channel-id=job-discord-event-test-channel",
        "app.discord.channel-policy.default-job-channel-key=job-notice",
    ],
)
class JobDiscordEventIntegrationTest {
    @Autowired
    private lateinit var jobService: JobService

    @Autowired
    private lateinit var companyRepository: CompanyRepository

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @MockitoBean
    private lateinit var discordDeliveryService: DiscordDeliveryService

    // Kotlin non-null 파라미터에 bare any()를 쓰면 null 반환으로 NPE가 나므로 Elvis로 기본값을
    // 채운다(InquiryAnswerNotificationFailureIntegrationTest.anyCommand()와 동일한 이유).
    private fun anyCommand(): DiscordDeliveryEnqueueCommand =
        any(DiscordDeliveryEnqueueCommand::class.java)
            ?: DiscordDeliveryEnqueueCommand(DiscordMessageTemplate.JOB_PUBLISHED, 0L, "")

    @Test
    fun `Discord 예약이 실패해도 공고 등록 Transaction은 이미 Commit되어 영향을 받지 않는다`() {
        given(discordDeliveryService.enqueue(anyCommand())).willThrow(RuntimeException("Discord 예약 강제 실패(Test 전용)"))

        val companyId = requireNotNull(createCompany().id)
        val teacherId = requireNotNull(createMember("job-discord-event-teacher").id)

        // create는 @Transactional Method라 반환 시점에는 이미 Commit이 끝나 있고, AFTER_COMMIT
        // Listener(JobDiscordEventListener)도 같은 호출 Thread 안에서 동기 실행을 마친 뒤다 --
        // 별도 Thread/Sleep 없이 반환값만 확인해도 된다.
        val response = jobService.create(publishedJobRequest(companyId), createdByMemberId = teacherId)

        assertThat(response.status).isEqualTo(JobStatus.PUBLISHED)
        assertThat(response.jobId).isNotNull()
    }

    private fun createCompany() = companyRepository.saveAndFlush(Company(name = "인력개발원", type = CompanyType.GENERAL))

    private fun createMember(subject: String): Member =
        memberRepository.saveAndFlush(
            Member(
                oauthProvider = OAuthProvider.DG,
                oauthSubject = "$subject-${UUID.randomUUID()}",
                email = "$subject-${UUID.randomUUID()}@example.com",
                status = MemberStatus.ACTIVE,
            ).apply { name = subject },
        )

    private fun publishedJobRequest(companyId: Long) =
        JobCreateRequest(
            companyId = companyId,
            postingType = PostingType.MOU,
            applicationMethod = ApplicationMethod.EXTERNAL,
            title = "Discord Event 연동 Test용 채용 공고",
            status = JobStatus.PUBLISHED,
            content = "본문",
            externalUrl = "https://example.com/apply",
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
