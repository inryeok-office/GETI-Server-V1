package team.inreok.getiserver.domain.file

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
import team.inreok.getiserver.domain.file.entity.type.FileOwnerType
import team.inreok.getiserver.domain.file.entity.type.FilePurpose
import team.inreok.getiserver.domain.file.entity.type.FileStatus
import team.inreok.getiserver.domain.file.exception.FileAlreadyLinkedException
import team.inreok.getiserver.domain.file.link.FileLinkPort
import team.inreok.getiserver.domain.file.link.FileSnapshot
import team.inreok.getiserver.domain.file.repository.StoredFileRepository
import team.inreok.getiserver.domain.member.entity.Member
import team.inreok.getiserver.domain.member.entity.type.OAuthProvider
import team.inreok.getiserver.domain.member.repository.MemberRepository
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 같은 파일을 서로 다른 리소스에 동시에 연결하려는 요청이 실제 PostgreSQL에서 순차화되는지
 * 검증한다(요구사항 §14 "이미 사용된 파일 재사용 방지").
 *
 * `FileLinkPortTest`는 Mock Repository라 DB Row Lock 자체를 재현할 수 없다. Lock 없이 조회하면
 * 두 Transaction이 모두 `UPLOADED` 상태를 읽어 검사를 통과하고, 나중 Commit이 먼저 것의
 * `owner_id`를 조용히 덮어쓴다(READ COMMITTED). `StoredFileRepository.findAllByIdInForUpdate`가
 * 그 경합을 막는지는 이 Test에서만 확인된다.
 */
@Testcontainers
@SpringBootTest(
    // ProgramApplicationConcurrencyIntegrationTest와 같은 이유로 MOCK이다. 실제 HTTP 요청은
    // 보내지 않고 Service Bean만 사용한다.
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = [
        "app.jwt.secret=file-link-concurrency-integration-test-only-jwt-secret-value",
        "app.jwt.access-token-expiration-seconds=1800",
        "app.jwt.refresh-token-expiration-seconds=1209600",
        // src/main/resources/application.yaml이 이 Classpath에서 우선하는데 app.file.storage는
        // Profile별 파일에만 있어 여기서 채운다. 실제 Storage에 접속하지 않고 Bean 생성만 필요하다.
        "app.file.storage.bucket=geti-integration-test",
        "app.file.storage.region=us-east-1",
        "app.file.storage.access-key=integration-test-only-access-key",
        "app.file.storage.secret-key=integration-test-only-secret-key",
    ],
)
class FileLinkConcurrencyIntegrationTest {
    @Autowired
    private lateinit var fileLinkPort: FileLinkPort

    @Autowired
    private lateinit var storedFileRepository: StoredFileRepository

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Test
    fun `같은 파일을 서로 다른 리소스에 동시에 연결하면 한 쪽만 성공한다`() {
        val uploaderId = saveUploader()
        val fileId = saveUploadedFile(uploaderId)

        val results = linkConcurrently(uploaderId, fileId, listOf(FIRST_OWNER_ID, SECOND_OWNER_ID))

        val successes = results.filter { it.isSuccess }
        val failures = results.mapNotNull { it.exceptionOrNull() }
        assertThat(successes).hasSize(1)
        assertThat(failures).hasSize(1)
        assertThat(failures.single()).isInstanceOf(FileAlreadyLinkedException::class.java)

        // 실제로 붙은 리소스가 성공한 쪽 하나뿐인지 DB에서 확인한다. Lost Update가 나면 두
        // 요청이 모두 성공하고 owner_id는 나중 것으로 덮어써진다.
        val stored = storedFileRepository.findById(fileId).orElseThrow()
        assertThat(stored.status).isEqualTo(FileStatus.LINKED)
        assertThat(stored.ownerId).isIn(FIRST_OWNER_ID, SECOND_OWNER_ID)
        assertThat(stored.ownerType).isEqualTo(FileOwnerType.JOB_APPLICATION)
    }

    private fun linkConcurrently(
        uploaderId: Long,
        fileId: Long,
        ownerIds: List<Long>,
    ): List<Result<List<FileSnapshot>>> {
        val executor = Executors.newFixedThreadPool(ownerIds.size)
        val ready = CountDownLatch(ownerIds.size)
        val start = CountDownLatch(1)
        val results = ConcurrentLinkedQueue<Result<List<FileSnapshot>>>()

        ownerIds.forEach { ownerId ->
            executor.submit {
                ready.countDown()
                start.await()
                results.add(
                    runCatching {
                        fileLinkPort.validateAndLink(
                            requesterId = uploaderId,
                            fileIds = listOf(fileId),
                            purpose = FilePurpose.JOB_APPLICATION,
                            ownerId = ownerId,
                        )
                    },
                )
            }
        }
        ready.await()
        start.countDown()
        executor.shutdown()
        check(executor.awaitTermination(30, TimeUnit.SECONDS)) { "동시 연결 Thread가 제한 시간 안에 끝나지 않았습니다." }
        return results.toList()
    }

    private fun saveUploadedFile(uploaderId: Long): Long {
        val file =
            storedFileRepository.saveAndFlush(
                StoredFile(
                    purpose = FilePurpose.JOB_APPLICATION,
                    objectKey = "JOB_APPLICATION/2026/08/${UUID.randomUUID()}",
                    originalName = "이력서.pdf",
                    contentType = "application/pdf",
                    sizeBytes = 1024L,
                    uploaderMemberId = uploaderId,
                    extension = "pdf",
                ).apply { markUploaded() },
            )
        return requireNotNull(file.id)
    }

    /** `files.uploader_member_id`에 실제 FK(fk_files_uploader_member)가 있어 Member가 먼저 필요하다. */
    private fun saveUploader(): Long {
        val member =
            memberRepository.saveAndFlush(
                Member(
                    oauthProvider = OAuthProvider.DG,
                    oauthSubject = "file-link-concurrency-uploader",
                    email = "file-link-concurrency-uploader@example.com",
                ),
            )
        return requireNotNull(member.id)
    }

    companion object {
        private const val FIRST_OWNER_ID = 100L
        private const val SECOND_OWNER_ID = 200L

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
