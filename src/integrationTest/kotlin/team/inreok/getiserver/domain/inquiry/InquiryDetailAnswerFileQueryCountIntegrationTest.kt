package team.inreok.getiserver.domain.inquiry

import com.redis.testcontainers.RedisContainer
import jakarta.persistence.EntityManagerFactory
import org.assertj.core.api.Assertions.assertThat
import org.hibernate.SessionFactory
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import team.inreok.getiserver.domain.file.entity.StoredFile
import team.inreok.getiserver.domain.file.entity.type.FileOwnerType
import team.inreok.getiserver.domain.file.entity.type.FilePurpose
import team.inreok.getiserver.domain.file.repository.StoredFileRepository
import team.inreok.getiserver.domain.inquiry.entity.Inquiry
import team.inreok.getiserver.domain.inquiry.entity.InquiryAnswer
import team.inreok.getiserver.domain.inquiry.entity.type.InquiryType
import team.inreok.getiserver.domain.inquiry.repository.InquiryAnswerRepository
import team.inreok.getiserver.domain.inquiry.repository.InquiryRepository
import team.inreok.getiserver.domain.inquiry.service.InquiryService
import team.inreok.getiserver.domain.member.entity.Member
import team.inreok.getiserver.domain.member.entity.type.MemberStatus
import team.inreok.getiserver.domain.member.entity.type.OAuthProvider
import team.inreok.getiserver.domain.member.repository.MemberRepository
import java.time.LocalDateTime
import java.util.UUID

/**
 * 문의 상세 조회의 답변 첨부파일 조회가 답변 수에 비례해 Query를 늘리지 않는지 **실제 JDBC
 * Statement 수를 세어** 확인한다.
 *
 * `InquiryServiceImpl.getDetail`은 답변 목록에 등장하는 모든 answerId를 모아
 * `FileLinkPort.linkedFilesOf(ownerType, ownerIds)` 배치 오버로드를 한 번만 호출하도록
 * 작성했지만(답변마다 개별 호출하면 N+1), 그 전제가 실제로 지켜지는지는 Mock을 쓰는 Test로는
 * 확인할 수 없다. `InquiryAdminListQueryCountIntegrationTest`(작성자·담당자 Snapshot 배치 조회
 * 고정)와 동일한 방식으로 실측 고정한다.
 */
@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = [
        "app.jwt.secret=inquiry-detail-answer-file-query-count-integration-test-only-jwt-secret-value",
        "app.jwt.access-token-expiration-seconds=1800",
        "app.jwt.refresh-token-expiration-seconds=1209600",
        // Presigned URL 서명은 Local 연산이라 실제 Storage 접속이 필요하지 않다. Bean 생성에
        // 필요한 값만 채운다(InquiryAdminListQueryCountIntegrationTest와 동일한 이유).
        "app.file.storage.bucket=geti-integration-test",
        "app.file.storage.region=us-east-1",
        "app.file.storage.access-key=integration-test-only-access-key",
        "app.file.storage.secret-key=integration-test-only-secret-key",
    ],
)
class InquiryDetailAnswerFileQueryCountIntegrationTest {
    @Autowired
    private lateinit var inquiryService: InquiryService

    @Autowired
    private lateinit var inquiryRepository: InquiryRepository

    @Autowired
    private lateinit var inquiryAnswerRepository: InquiryAnswerRepository

    @Autowired
    private lateinit var storedFileRepository: StoredFileRepository

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Autowired
    private lateinit var entityManagerFactory: EntityManagerFactory

    @Test
    fun `답변 수가 늘어도 답변 첨부파일 조회 Query 수는 늘지 않는다`() {
        val author = createMember("detail-file-count-author")
        val authorId = requireNotNull(author.id)
        val inquiryId = createInquiry(authorId)
        repeat(FEW) { createAnswerWithFile(inquiryId, authorId) }

        val statistics = entityManagerFactory.unwrap(SessionFactory::class.java).statistics
        statistics.isStatisticsEnabled = true

        val fewResult = getDetailAndCount(statistics, inquiryId, authorId)

        repeat(MANY - FEW) { createAnswerWithFile(inquiryId, authorId) }
        val manyResult = getDetailAndCount(statistics, inquiryId, authorId)

        // 전제 확인: 답변이 실제로 요청한 개수만큼 늘었다. 그렇지 않으면 이 측정 자체가 무의미해진다.
        assertThat(fewResult.answerCount).isEqualTo(FEW)
        assertThat(manyResult.answerCount).isEqualTo(MANY)

        // 핵심: 답변 수가 늘어도 Statement 수는 그대로여야 한다. 답변마다 첨부파일을 개별
        // 조회한다면 차이가 (MANY - FEW)에 비례해 벌어진다.
        assertThat(manyResult.statements)
            .describedAs(
                "답변 %d건 조회가 %d건 조회보다 Statement를 더 쓰면 안 된다 (few=%d, many=%d)",
                MANY,
                FEW,
                fewResult.statements,
                manyResult.statements,
            ).isEqualTo(fewResult.statements)
    }

    private fun getDetailAndCount(
        statistics: org.hibernate.stat.Statistics,
        inquiryId: Long,
        authorId: Long,
    ): DetailMeasurement {
        val before = statistics.prepareStatementCount
        val result = inquiryService.getDetail(inquiryId, requesterMemberId = authorId, isDeveloper = false)
        return DetailMeasurement(
            statements = statistics.prepareStatementCount - before,
            answerCount = result.answers.size,
        )
    }

    private fun createInquiry(authorMemberId: Long): Long {
        val saved =
            inquiryRepository.saveAndFlush(
                Inquiry(
                    authorMemberId = authorMemberId,
                    type = InquiryType.ETC,
                    title = "Query 수 측정용 문의",
                    content = "답변 첨부파일 N+1 방지 확인용 문의입니다.",
                ),
            )
        return requireNotNull(saved.id)
    }

    private fun createAnswerWithFile(
        inquiryId: Long,
        authorMemberId: Long,
    ) {
        val answer =
            inquiryAnswerRepository.saveAndFlush(
                InquiryAnswer(inquiryId = inquiryId, authorMemberId = authorMemberId, content = "답변 내용"),
            )
        val answerId = requireNotNull(answer.id)
        val file =
            StoredFile(
                purpose = FilePurpose.INQUIRY_ANSWER_ATTACHMENT,
                objectKey = "inquiry-answer-attachment/test/${UUID.randomUUID()}",
                originalName = "answer.png",
                contentType = "image/png",
                sizeBytes = 1024L,
                uploaderMemberId = authorMemberId,
                extension = "png",
            ).apply {
                markUploaded()
                linkTo(FileOwnerType.INQUIRY_ANSWER, answerId, LocalDateTime.now())
            }
        storedFileRepository.saveAndFlush(file)
    }

    private fun createMember(subject: String): Member =
        memberRepository.saveAndFlush(
            Member(
                oauthProvider = OAuthProvider.DG,
                oauthSubject = subject,
                email = "$subject@example.com",
                status = MemberStatus.ACTIVE,
            ).apply { name = subject },
        )

    private data class DetailMeasurement(
        val statements: Long,
        val answerCount: Int,
    )

    companion object {
        private const val FEW = 2
        private const val MANY = 6

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
