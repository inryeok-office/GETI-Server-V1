package team.inreok.getiserver.domain.inquiry

import com.redis.testcontainers.RedisContainer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import team.inreok.getiserver.domain.inquiry.dto.InquiryCreateRequest
import team.inreok.getiserver.domain.inquiry.entity.type.InquiryDiscordDeliveryStatus
import team.inreok.getiserver.domain.inquiry.entity.type.InquiryType
import team.inreok.getiserver.domain.inquiry.repository.InquiryRepository
import team.inreok.getiserver.domain.inquiry.service.InquiryDiscordDeliveryQueryPort
import team.inreok.getiserver.domain.inquiry.service.InquiryService
import team.inreok.getiserver.domain.member.entity.Member
import team.inreok.getiserver.domain.member.entity.type.MemberStatus
import team.inreok.getiserver.domain.member.entity.type.OAuthProvider
import team.inreok.getiserver.domain.member.repository.MemberRepository
import java.util.UUID

/**
 * Discord 상태 조회([InquiryDiscordDeliveryQueryPort])가 실패해도 문의 등록
 * (`InquiryServiceImpl.create`) Transaction은 Rollback되지 않아야 한다(요구사항 §42, Phase 4의
 * `InquiryAnswerNotificationFailureIntegrationTest`와 동일한 방식, 사용자 확인 완료).
 *
 * Phase 4의 Notification 격리와 달리, Discord 상태 조회는 즉시 응답에 담아야 하는 값(`discordDeliveryStatus`)
 * 이라 `create()`와 같은 Transaction 안에서 동기 호출된다 -- `AFTER_COMMIT` Listener로 미룰 수 없다.
 * 그래서 격리 방식도 다르다: Event/Listener 분리가 아니라 `InquiryServiceImpl.safeDiscordStatus`의
 * `runCatching` 방어로 실패를 흡수하고 기본값(PENDING)으로 대체한다. 이 Test는 그 방어가 실제로
 * 동작해 문의 INSERT까지 함께 Rollback되지 않는지 확인한다.
 */
@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = [
        "app.jwt.secret=inquiry-creation-discord-failure-integration-test-only-jwt-secret-value",
        "app.jwt.access-token-expiration-seconds=1800",
        "app.jwt.refresh-token-expiration-seconds=1209600",
        "app.file.storage.bucket=geti-integration-test",
        "app.file.storage.region=us-east-1",
        "app.file.storage.access-key=integration-test-only-access-key",
        "app.file.storage.secret-key=integration-test-only-secret-key",
    ],
)
class InquiryCreationDiscordFailureIntegrationTest {
    @Autowired
    private lateinit var inquiryService: InquiryService

    @Autowired
    private lateinit var inquiryRepository: InquiryRepository

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @MockitoBean
    private lateinit var inquiryDiscordDeliveryQueryPort: InquiryDiscordDeliveryQueryPort

    @Test
    fun `Discord 상태 조회가 실패해도 문의 등록 Transaction은 Rollback되지 않고 PENDING으로 대체된다`() {
        given(inquiryDiscordDeliveryQueryPort.statusOf(anyLong()))
            .willThrow(RuntimeException("Discord 상태 조회 강제 실패(Test 전용)"))

        val author = createMember("discord-fail-author")

        val response =
            inquiryService.create(
                InquiryCreateRequest(
                    inquiryType = InquiryType.ETC,
                    title = "Discord 조회 실패와 무관하게 등록되어야 하는 문의",
                    content = "Discord 상태 조회 실패가 문의 등록을 막지 않는지 확인합니다.",
                ),
                requireNotNull(author.id),
            )

        // 실패를 흡수하고 기본값(PENDING)으로 대체됐는지 확인한다.
        assertThat(response.discordDeliveryStatus).isEqualTo(InquiryDiscordDeliveryStatus.PENDING)
        assertThat(response.discordDelivered).isFalse()

        // 문의 자체는 실제로 Commit됐는지 확인한다.
        val savedInquiry = requireNotNull(inquiryRepository.findById(response.inquiryId).orElse(null))
        assertThat(savedInquiry.title).isEqualTo("Discord 조회 실패와 무관하게 등록되어야 하는 문의")
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
