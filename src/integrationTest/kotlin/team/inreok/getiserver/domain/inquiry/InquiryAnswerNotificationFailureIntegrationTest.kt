package team.inreok.getiserver.domain.inquiry

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
import team.inreok.getiserver.domain.inquiry.dto.InquiryAnswerCreateRequest
import team.inreok.getiserver.domain.inquiry.entity.Inquiry
import team.inreok.getiserver.domain.inquiry.entity.type.InquiryStatus
import team.inreok.getiserver.domain.inquiry.entity.type.InquiryType
import team.inreok.getiserver.domain.inquiry.repository.InquiryAnswerRepository
import team.inreok.getiserver.domain.inquiry.repository.InquiryRepository
import team.inreok.getiserver.domain.inquiry.service.InquiryService
import team.inreok.getiserver.domain.member.entity.Member
import team.inreok.getiserver.domain.member.entity.type.MemberStatus
import team.inreok.getiserver.domain.member.entity.type.OAuthProvider
import team.inreok.getiserver.domain.member.repository.MemberRepository
import team.inreok.getiserver.domain.notification.dto.NotificationCreateCommand
import team.inreok.getiserver.domain.notification.entity.type.NotificationType
import team.inreok.getiserver.domain.notification.service.NotificationService
import java.util.UUID

/**
 * `InquiryAnsweredNotificationListener`(`domain.notification`)가 Notification 저장에 실패해도
 * 답변 등록(`InquiryServiceImpl.createAnswer`) Transaction은 이미 Commit된 뒤이므로 영향을 받지
 * 않아야 한다(요구사항 §67, 사용자 확인 완료). `@TransactionalEventListener(phase = AFTER_COMMIT)`가
 * 실제로 원본 Transaction과 분리돼 있는지는 Mock만으로는 확인할 수 없어 실제 PostgreSQL Commit
 * 경계로 검증한다.
 *
 * `NotificationService`를 강제로 실패하는 Mock으로 대체한다 -- 알림 저장 자체가 아니라 "알림
 * 저장 실패가 답변 저장에 전파되는가"가 관심사이므로, Notification 쪽 실제 저장 로직까지
 * 재현할 필요는 없다.
 */
@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = [
        "app.jwt.secret=inquiry-answer-notification-failure-integration-test-only-jwt-secret-value",
        "app.jwt.access-token-expiration-seconds=1800",
        "app.jwt.refresh-token-expiration-seconds=1209600",
        "app.file.storage.bucket=geti-integration-test",
        "app.file.storage.region=us-east-1",
        "app.file.storage.access-key=integration-test-only-access-key",
        "app.file.storage.secret-key=integration-test-only-secret-key",
    ],
)
class InquiryAnswerNotificationFailureIntegrationTest {
    @Autowired
    private lateinit var inquiryService: InquiryService

    @Autowired
    private lateinit var inquiryRepository: InquiryRepository

    @Autowired
    private lateinit var inquiryAnswerRepository: InquiryAnswerRepository

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @MockitoBean
    private lateinit var notificationService: NotificationService

    // Kotlin non-null 파라미터에 bare any()를 쓰면 null 반환으로 NPE가 나므로 Elvis로 기본값을
    // 채운다(InquiryControllerTest.anyCreateRequest()와 동일한 이유).
    private fun anyCommand(): NotificationCreateCommand =
        any(NotificationCreateCommand::class.java)
            ?: NotificationCreateCommand(
                recipientMemberId = 0L,
                type = NotificationType.SYSTEM,
                title = "",
                content = "",
            )

    @Test
    fun `알림 생성이 실패해도 답변 등록 Transaction은 이미 Commit되어 영향을 받지 않는다`() {
        given(notificationService.create(anyCommand())).willThrow(RuntimeException("알림 저장 강제 실패(Test 전용)"))

        val author = createMember("notif-fail-author")
        val developer = createMember("notif-fail-developer")
        val inquiryId = createInquiry(requireNotNull(author.id), InquiryStatus.RECEIVED)

        // createAnswer는 @Transactional Method라 반환 시점에는 이미 Commit이 끝나 있고,
        // AFTER_COMMIT Listener(InquiryAnsweredNotificationListener)도 같은 호출 Thread 안에서
        // 동기 실행을 마친 뒤다 -- 별도 Thread/Sleep 없이 반환값만 확인해도 된다.
        val response =
            inquiryService.createAnswer(
                inquiryId,
                InquiryAnswerCreateRequest(content = "알림 실패와 무관하게 저장되어야 하는 답변"),
                requireNotNull(developer.id),
            )

        assertThat(response.inquiryStatus).isEqualTo(InquiryStatus.ANSWERED)

        val savedAnswers = inquiryAnswerRepository.findAllByInquiryIdOrderByCreatedAtAscIdAsc(inquiryId)
        assertThat(savedAnswers).hasSize(1)
        assertThat(savedAnswers.single().content).isEqualTo("알림 실패와 무관하게 저장되어야 하는 답변")

        val finalInquiry = requireNotNull(inquiryRepository.findById(inquiryId).orElse(null))
        assertThat(finalInquiry.status).isEqualTo(InquiryStatus.ANSWERED)
        assertThat(finalInquiry.answeredAt).isNotNull()
    }

    private fun createInquiry(
        authorMemberId: Long,
        status: InquiryStatus,
    ): Long {
        val saved =
            inquiryRepository.saveAndFlush(
                Inquiry(
                    authorMemberId = authorMemberId,
                    type = InquiryType.ETC,
                    title = "알림 실패 격리 Test용 문의",
                    content = "Notification 저장 실패가 답변 Transaction에 전파되지 않는지 확인합니다.",
                    status = status,
                ),
            )
        return requireNotNull(saved.id)
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
