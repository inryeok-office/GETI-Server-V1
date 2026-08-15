package team.inreok.getiserver.domain.program

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
import team.inreok.getiserver.domain.file.entity.type.FileStatus
import team.inreok.getiserver.domain.file.repository.StoredFileRepository
import team.inreok.getiserver.domain.member.entity.Member
import team.inreok.getiserver.domain.member.entity.type.OAuthProvider
import team.inreok.getiserver.domain.member.repository.MemberRepository
import team.inreok.getiserver.domain.program.dto.ProgramCreateRequest
import team.inreok.getiserver.domain.program.dto.ProgramStatusUpdateRequest
import team.inreok.getiserver.domain.program.entity.type.ProgramStatus
import team.inreok.getiserver.domain.program.entity.type.ProgramType
import team.inreok.getiserver.domain.program.service.ProgramService
import java.util.UUID

/**
 * Program 삭제(Soft Delete, `changeStatus(status=DELETED)`) 시 본문 첨부파일 연결이 실제
 * PostgreSQL에서 해제되는지 검증한다(Issue #127). `ProgramServiceImplTest`는 Mock Repository라
 * `FileLinkPort.unlinkAllOf` 호출 여부만 확인할 수 있고, `StoredFile.status`/`ownerId`가 실제로
 * 되돌아가는지는 이 Test에서만 확인된다.
 */
@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = [
        "app.jwt.secret=program-file-deletion-integration-test-only-jwt-secret-value",
        "app.jwt.access-token-expiration-seconds=1800",
        "app.jwt.refresh-token-expiration-seconds=1209600",
        "app.file.storage.bucket=geti-integration-test",
        "app.file.storage.region=us-east-1",
        "app.file.storage.access-key=integration-test-only-access-key",
        "app.file.storage.secret-key=integration-test-only-secret-key",
    ],
)
class ProgramFileDeletionIntegrationTest {
    @Autowired
    private lateinit var programService: ProgramService

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Autowired
    private lateinit var storedFileRepository: StoredFileRepository

    @Test
    fun `프로그램을 삭제하면 연결된 첨부파일의 연결이 해제된다`() {
        val teacherId = requireNotNull(createMember("program-file-deletion-teacher").id)
        val fileId = saveUploadedFile(teacherId)

        val created =
            programService.create(
                ProgramCreateRequest(
                    title = "첨부파일 삭제 연동 Test용 특강",
                    programType = ProgramType.SPECIAL_LECTURE,
                    status = ProgramStatus.DRAFT,
                    fileIds = listOf(fileId),
                ),
                teacherId,
            )
        val programId = requireNotNull(created.programId)

        val linkedBeforeDelete = storedFileRepository.findById(fileId).orElseThrow()
        assertThat(linkedBeforeDelete.status).isEqualTo(FileStatus.LINKED)
        assertThat(linkedBeforeDelete.ownerId).isEqualTo(programId)

        programService.changeStatus(
            programId,
            requesterMemberId = teacherId,
            isDeveloper = false,
            request = ProgramStatusUpdateRequest(status = ProgramStatus.DELETED),
        )

        // Binary는 지우지 않는다 -- Row 자체는 남고 연결만 UPLOADED로 되돌아간다
        // (FileLinkPort.unlinkAllOf KDoc 참고).
        val unlinkedAfterDelete = storedFileRepository.findById(fileId).orElseThrow()
        assertThat(unlinkedAfterDelete.status).isEqualTo(FileStatus.UPLOADED)
        assertThat(unlinkedAfterDelete.ownerId).isNull()
        assertThat(unlinkedAfterDelete.ownerType).isNull()
    }

    private fun saveUploadedFile(uploaderId: Long): Long {
        val file =
            storedFileRepository.saveAndFlush(
                StoredFile(
                    purpose = FilePurpose.PROGRAM_ATTACHMENT,
                    objectKey = "PROGRAM_ATTACHMENT/2026/08/${UUID.randomUUID()}",
                    originalName = "안내문.pdf",
                    contentType = "application/pdf",
                    sizeBytes = 1024L,
                    uploaderMemberId = uploaderId,
                    extension = "pdf",
                ).apply { markUploaded() },
            )
        return requireNotNull(file.id)
    }

    /** `files.uploader_member_id`에 실제 FK(fk_files_uploader_member)가 있어 Member가 먼저 필요하다. */
    private fun createMember(subject: String): Member =
        memberRepository.saveAndFlush(
            Member(
                oauthProvider = OAuthProvider.DG,
                oauthSubject = "$subject-${UUID.randomUUID()}",
                email = "$subject-${UUID.randomUUID()}@example.com",
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
