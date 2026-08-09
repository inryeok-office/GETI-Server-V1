package team.inreok.getiserver.domain.inquiry

import com.redis.testcontainers.RedisContainer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import team.inreok.getiserver.domain.file.entity.StoredFile
import team.inreok.getiserver.domain.file.entity.type.FilePurpose
import team.inreok.getiserver.domain.file.entity.type.FileStatus
import team.inreok.getiserver.domain.file.repository.StoredFileRepository
import team.inreok.getiserver.domain.inquiry.dto.InquiryAnswerCreateRequest
import team.inreok.getiserver.domain.inquiry.dto.InquiryStatusUpdateRequest
import team.inreok.getiserver.domain.inquiry.entity.Inquiry
import team.inreok.getiserver.domain.inquiry.entity.type.InquiryStatus
import team.inreok.getiserver.domain.inquiry.entity.type.InquiryType
import team.inreok.getiserver.domain.inquiry.exception.InquiryAlreadyClosedException
import team.inreok.getiserver.domain.inquiry.repository.InquiryAnswerRepository
import team.inreok.getiserver.domain.inquiry.repository.InquiryRepository
import team.inreok.getiserver.domain.inquiry.service.InquiryService
import team.inreok.getiserver.domain.member.entity.Member
import team.inreok.getiserver.domain.member.entity.type.MemberStatus
import team.inreok.getiserver.domain.member.entity.type.OAuthProvider
import team.inreok.getiserver.domain.member.repository.MemberRepository
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * POST .../answers(답변 등록)와 PATCH .../status(CLOSED 전환)가 같은 Inquiry Row를 동시에 건드릴
 * 때의 정합성을 **실제 PostgreSQL Row Lock**으로 검증한다(§CLOSED-답변 동시성, Row Lock 방식 채택
 * -- 사용자 확인 완료). `InquiryRepository.findByIdForUpdate`(Pessimistic Write Lock)가 두
 * Transaction을 순차화하므로, 먼저 Commit되는 쪽이 이기고 나중 요청은 그 결과를 반영해 정합성
 * 있게 성공하거나 거부돼야 한다.
 *
 * 두 Test 모두 "이기는 쪽"의 Commit 시점을 `Thread.sleep`으로 의도적으로 늦춰 "지는 쪽"이 실제로
 * Lock 대기 상태에 들어가게 만든다. Production Code(`InquiryServiceImpl`)에 Test 전용 지연 Hook을
 * 추가하지 않기 위해, 이기는 쪽은 `InquiryRepository.findByIdForUpdate`(실제 Service가 쓰는 것과
 * 동일한 Method)로 직접 Lock을 걸고 상태만 바꾸는 최소 재현으로 대신한다 -- Lock 획득과 상태 변경은
 * 실제 Production 경로와 동일하고, 그 사이 대기 시간만 Test가 통제한다. 지는 쪽은 항상 실제
 * `InquiryService` Method를 그대로 호출해 실제 동작을 검증한다.
 */
@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = [
        "app.jwt.secret=inquiry-answer-concurrency-integration-test-only-jwt-secret-value",
        "app.jwt.access-token-expiration-seconds=1800",
        "app.jwt.refresh-token-expiration-seconds=1209600",
        "app.file.storage.bucket=geti-integration-test",
        "app.file.storage.region=us-east-1",
        "app.file.storage.access-key=integration-test-only-access-key",
        "app.file.storage.secret-key=integration-test-only-secret-key",
    ],
)
class InquiryAnswerConcurrencyIntegrationTest {
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

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    private val transactionTemplate: TransactionTemplate by lazy { TransactionTemplate(transactionManager) }

    @Test
    fun `CLOSED 전환이 먼저 Commit되면 동시에 등록을 시도한 답변은 409로 실패하고 답변·첨부파일 연결 모두 남지 않는다`() {
        val author = createMember("closed-wins-author")
        val developer = createMember("closed-wins-developer")
        val inquiryId = createInquiry(requireNotNull(author.id), InquiryStatus.IN_PROGRESS)
        // fileLinkPort.validateAndLink는 createAnswer와 같은 Transaction(REQUIRED 전파)에서
        // 실행되므로, CLOSED로 판명돼 Transaction 전체가 Rollback되면 InquiryAnswer INSERT뿐
        // 아니라 이 파일의 files.owner_type/owner_id/status 변경도 함께 Rollback돼야 한다 --
        // 그렇지 않으면 답변은 없는데 파일만 LINKED로 남는 고아 연결이 생긴다(사용자 확인 요청).
        val fileId = createUploadedFile(requireNotNull(developer.id))

        val lockAcquired = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val closeFuture =
                executor.submit {
                    transactionTemplate.execute {
                        val locked = requireNotNull(inquiryRepository.findByIdForUpdate(inquiryId))
                        lockAcquired.countDown()
                        Thread.sleep(LOCK_HOLD_MILLIS)
                        locked.status = InquiryStatus.CLOSED
                        inquiryRepository.flush()
                    }
                }

            assertThat(lockAcquired.await(AWAIT_SECONDS, TimeUnit.SECONDS)).isTrue()
            val answerFuture =
                executor.submit(
                    Callable {
                        runCatching {
                            inquiryService.createAnswer(
                                inquiryId,
                                InquiryAnswerCreateRequest(content = "동시성 Test 답변", fileIds = listOf(fileId)),
                                requireNotNull(developer.id),
                            )
                        }
                    },
                )

            closeFuture.get(AWAIT_SECONDS, TimeUnit.SECONDS)
            val answerResult = answerFuture.get(AWAIT_SECONDS, TimeUnit.SECONDS)

            assertThat(answerResult.isFailure).isTrue()
            assertThat(answerResult.exceptionOrNull()).isInstanceOf(InquiryAlreadyClosedException::class.java)

            val finalInquiry = requireNotNull(inquiryRepository.findById(inquiryId).orElse(null))
            assertThat(finalInquiry.status).isEqualTo(InquiryStatus.CLOSED)
            // 답변 INSERT까지 포함된 Transaction 전체가 Rollback됐는지 확인한다.
            assertThat(inquiryAnswerRepository.findAllByInquiryIdOrderByCreatedAtAscIdAsc(inquiryId)).isEmpty()
            // 파일 연결(files.owner_type/owner_id/status=LINKED)도 같은 Transaction 안에서
            // 함께 Rollback돼 UPLOADED(미연결) 상태 그대로 남아야 한다 -- 고아 연결이 없어야 한다.
            val finalFile = requireNotNull(storedFileRepository.findById(fileId).orElse(null))
            assertThat(finalFile.status).isEqualTo(FileStatus.UPLOADED)
            assertThat(finalFile.ownerType).isNull()
            assertThat(finalFile.ownerId).isNull()
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `답변 등록이 먼저 Commit되면 동시에 시도한 CLOSED 전환은 정상 성공한다`() {
        val author = createMember("answer-wins-author")
        val inquiryId = createInquiry(requireNotNull(author.id), InquiryStatus.IN_PROGRESS)

        val lockAcquired = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            // 실제 createAnswer가 Commit 직전에 Inquiry에 반영하는 값(status=ANSWERED,
            // answeredAt)만 재현한다. InquiryAnswer INSERT 자체는 이 Test의 관심사(CLOSED 전환
            // 쪽 결과)가 아니라 생략한다.
            val answerFuture =
                executor.submit {
                    transactionTemplate.execute {
                        val locked = requireNotNull(inquiryRepository.findByIdForUpdate(inquiryId))
                        lockAcquired.countDown()
                        Thread.sleep(LOCK_HOLD_MILLIS)
                        locked.status = InquiryStatus.ANSWERED
                        locked.answeredAt = LocalDateTime.now()
                        inquiryRepository.flush()
                    }
                }

            assertThat(lockAcquired.await(AWAIT_SECONDS, TimeUnit.SECONDS)).isTrue()
            val closeFuture =
                executor.submit(
                    Callable {
                        inquiryService.changeStatus(
                            inquiryId,
                            InquiryStatusUpdateRequest(status = InquiryStatus.CLOSED),
                        )
                    },
                )

            answerFuture.get(AWAIT_SECONDS, TimeUnit.SECONDS)
            val closeResult = closeFuture.get(AWAIT_SECONDS, TimeUnit.SECONDS)

            assertThat(closeResult.status).isEqualTo(InquiryStatus.CLOSED)
            val finalInquiry = requireNotNull(inquiryRepository.findById(inquiryId).orElse(null))
            assertThat(finalInquiry.status).isEqualTo(InquiryStatus.CLOSED)
        } finally {
            executor.shutdownNow()
        }
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
                    title = "동시성 Test용 문의",
                    content = "CLOSED-답변 동시성 확인용 문의입니다.",
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
        // Lock을 쥔 쪽이 실제로 대기시키는 시간이다. 상대 Thread가 findByIdForUpdate를 호출해 진짜
        // Lock 대기 상태에 들어갈 시간을 확보하기 위함이다(단순 우연한 순차 실행과 구분).
        private const val LOCK_HOLD_MILLIS = 500L
        private const val AWAIT_SECONDS = 10L

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
