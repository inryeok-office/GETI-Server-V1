package team.inreok.getiserver.domain.inquiry

import com.redis.testcontainers.RedisContainer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import team.inreok.getiserver.domain.file.entity.StoredFile
import team.inreok.getiserver.domain.file.entity.type.FilePurpose
import team.inreok.getiserver.domain.file.exception.FileNotOwnedException
import team.inreok.getiserver.domain.file.repository.StoredFileRepository
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
import java.util.UUID

/**
 * fileIds 소유권 검증(File 도메인 Port 호출)이 CLOSED 검증보다 먼저 실행되는지 회귀 고정한다
 * (§CLOSED-답변 동시성 지시사항 4번, 사용자 확인 완료). 이미 CLOSED된 문의에, 답변 작성자
 * 본인이 업로드하지 않은 fileId를 함께 보내면 -- 두 위반이 동시에 성립하는 상황에서 -- 응답은
 * 항상 파일 소유권 오류(FILE_NOT_OWNED, 403)여야 하고 INQUIRY_ALREADY_CLOSED(409)가 먼저 나오면
 * 안 된다.
 */
@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = [
        "app.jwt.secret=inquiry-answer-file-validation-order-integration-test-only-jwt-secret-value",
        "app.jwt.access-token-expiration-seconds=1800",
        "app.jwt.refresh-token-expiration-seconds=1209600",
        "app.file.storage.bucket=geti-integration-test",
        "app.file.storage.region=us-east-1",
        "app.file.storage.access-key=integration-test-only-access-key",
        "app.file.storage.secret-key=integration-test-only-secret-key",
    ],
)
class InquiryAnswerFileValidationOrderIntegrationTest {
    @Autowired
    private lateinit var inquiryService: InquiryService

    @Autowired
    private lateinit var inquiryRepository: InquiryRepository

    @Autowired
    private lateinit var inquiryAnswerRepository: InquiryAnswerRepository

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Autowired
    private lateinit var storedFileRepository: StoredFileRepository

    @Test
    fun `이미 CLOSED된 문의라도 소유하지 않은 파일을 첨부하면 FILE_NOT_OWNED가 먼저 반환된다`() {
        val author = createMember("file-order-author")
        val fileOwner = createMember("file-order-file-owner")
        val developer = createMember("file-order-developer")
        val inquiryId = createInquiry(requireNotNull(author.id), InquiryStatus.CLOSED)
        val fileId = createUploadedFile(requireNotNull(fileOwner.id))

        val thrown =
            runCatching {
                inquiryService.createAnswer(
                    inquiryId,
                    InquiryAnswerCreateRequest(content = "검증 순서 확인용 답변", fileIds = listOf(fileId)),
                    requireNotNull(developer.id),
                )
            }.exceptionOrNull()

        assertThat(thrown).isInstanceOf(FileNotOwnedException::class.java)
        // 답변 INSERT까지 포함된 Transaction 전체가 Rollback됐는지 확인한다.
        assertThat(inquiryAnswerRepository.findAllByInquiryIdOrderByCreatedAtAscIdAsc(inquiryId)).isEmpty()
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
                    title = "검증 순서 확인용 문의",
                    content = "fileIds 소유권 검증이 CLOSED 검증보다 먼저인지 확인합니다.",
                    status = status,
                ),
            )
        return requireNotNull(saved.id)
    }

    private fun createUploadedFile(uploaderMemberId: Long): Long {
        val file =
            StoredFile(
                purpose = FilePurpose.INQUIRY_ANSWER_ATTACHMENT,
                objectKey = "inquiry-answer-attachment/test/${UUID.randomUUID()}",
                originalName = "answer.png",
                contentType = "image/png",
                sizeBytes = 1024L,
                uploaderMemberId = uploaderMemberId,
                extension = "png",
            ).apply { markUploaded() }
        return requireNotNull(storedFileRepository.saveAndFlush(file).id)
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
