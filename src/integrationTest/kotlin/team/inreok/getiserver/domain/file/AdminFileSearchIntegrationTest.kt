package team.inreok.getiserver.domain.file

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.data.domain.PageRequest
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import team.inreok.getiserver.domain.file.entity.StoredFile
import team.inreok.getiserver.domain.file.entity.type.FilePurpose
import team.inreok.getiserver.domain.file.entity.type.FileStatus
import team.inreok.getiserver.domain.file.repository.StoredFileRepository
import java.util.UUID

/**
 * 관리자 공통 파일 목록 조회(`StoredFileRepository.searchForAdmin`, Issue #225)가 실제
 * PostgreSQL에서 Filter·검색·정렬·Pagination을 의도대로 처리하는지 검증한다. `src/test`(H2)는 LIKE
 * ESCAPE 구문과 Enum Mapping을 PostgreSQL과 동일하게 보장하지 않아(docs/development/persistence.md)
 * 이 Test가 필요하다.
 */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration::class)
class AdminFileSearchIntegrationTest
    @Autowired
    constructor(
        private val fileRepository: StoredFileRepository,
    ) {
        @BeforeEach
        fun setUp() {
            fileRepository.deleteAll()
            fileRepository.flush()
        }

        @Test
        fun `Filter가 없으면 모든 상태의 파일을 최신순으로 조회한다`() {
            val pending = fileRepository.saveAndFlush(fileOf(name = "a.pdf"))
            val uploaded = fileRepository.saveAndFlush(fileOf(name = "b.pdf").apply { markUploaded() })
            val failed = fileRepository.saveAndFlush(fileOf(name = "c.pdf").apply { markFailed() })

            val page = search()

            // 일반 사용자 조회(isVisible())와 달리 PENDING/FAILED도 걸러내지 않고 모두 나온다.
            assertThat(page.content.map { it.id }).containsExactly(failed.id, uploaded.id, pending.id)
        }

        @Test
        fun `status로 걸러낸다`() {
            fileRepository.saveAndFlush(fileOf(name = "a.pdf"))
            val uploaded = fileRepository.saveAndFlush(fileOf(name = "b.pdf").apply { markUploaded() })

            val page = search(status = FileStatus.UPLOADED)

            assertThat(page.content.map { it.id }).containsExactly(uploaded.id)
        }

        @Test
        fun `purpose로 걸러낸다`() {
            fileRepository.saveAndFlush(fileOf(name = "a.pdf", purpose = FilePurpose.JOB_APPLICATION))
            val profileImage =
                fileRepository.saveAndFlush(fileOf(name = "b.png", purpose = FilePurpose.PROFILE_IMAGE))

            val page = search(purpose = FilePurpose.PROFILE_IMAGE)

            assertThat(page.content.map { it.id }).containsExactly(profileImage.id)
        }

        @Test
        fun `원본 파일명을 대소문자 구분 없이 부분 검색한다`() {
            val resume = fileRepository.saveAndFlush(fileOf(name = "Resume_Final.PDF"))
            fileRepository.saveAndFlush(fileOf(name = "other.pdf"))

            val page = search(hasOriginalName = true, originalName = "resume")

            assertThat(page.content.map { it.id }).containsExactly(resume.id)
        }

        @Test
        fun `검색어의 LIKE Wildcard는 문자 그대로 매칭된다`() {
            // "%"와 "_"를 그대로 포함한 파일명이 다른 이름까지 함께 매칭되지 않아야 한다
            // (JobApplicationRepository.search와 동일한 이스케이프 계약).
            val exact = fileRepository.saveAndFlush(fileOf(name = "100%_ok.pdf"))
            fileRepository.saveAndFlush(fileOf(name = "100X_ok.pdf"))

            val page = search(hasOriginalName = true, originalName = "100\\%\\_ok")

            assertThat(page.content.map { it.id }).containsExactly(exact.id)
        }

        @Test
        fun `Filter는 함께 AND로 조합된다`() {
            val target =
                fileRepository.saveAndFlush(
                    fileOf(name = "resume.pdf", purpose = FilePurpose.JOB_APPLICATION).apply { markUploaded() },
                )
            // status는 맞지만 purpose가 다르다.
            fileRepository.saveAndFlush(
                fileOf(name = "resume.png", purpose = FilePurpose.PROFILE_IMAGE).apply { markUploaded() },
            )
            // purpose는 맞지만 status가 다르다(PENDING).
            fileRepository.saveAndFlush(fileOf(name = "resume2.pdf", purpose = FilePurpose.JOB_APPLICATION))

            val page =
                search(
                    status = FileStatus.UPLOADED,
                    purpose = FilePurpose.JOB_APPLICATION,
                    hasOriginalName = true,
                    originalName = "resume",
                )

            assertThat(page.content.map { it.id }).containsExactly(target.id)
        }

        @Test
        fun `Pagination은 전체 건수를 기준으로 계산한다`() {
            repeat(3) { index -> fileRepository.saveAndFlush(fileOf(name = "file-$index.pdf")) }

            val page = search(pageable = PageRequest.of(1, 2))

            assertThat(page.content).hasSize(1)
            assertThat(page.totalElements).isEqualTo(3)
            assertThat(page.totalPages).isEqualTo(2)
            assertThat(page.isLast).isTrue()
        }

        private fun search(
            status: FileStatus? = null,
            purpose: FilePurpose? = null,
            hasOriginalName: Boolean = false,
            originalName: String = "",
            pageable: PageRequest = PageRequest.of(0, 20),
        ) = fileRepository.searchForAdmin(status, purpose, hasOriginalName, originalName, pageable)

        // uploaderMemberId는 members를 가리키는 FK(fk_files_uploader_member)가 있어 실제 Member
        // Row 없이는 null만 안전하게 쓸 수 있다(FileSchemaIntegrationTest.pendingFile과 동일한 관례).
        // 이 Test는 uploaderMemberId를 Filter/검증 대상으로 삼지 않으므로 null로 충분하다.
        private fun fileOf(
            name: String,
            purpose: FilePurpose = FilePurpose.JOB_APPLICATION,
        ) = StoredFile(
            purpose = purpose,
            objectKey = "$purpose/2026/08/${UUID.randomUUID()}",
            originalName = name,
            contentType = "application/pdf",
            sizeBytes = 1024L,
            uploaderMemberId = null,
            extension = "pdf",
        )

        companion object {
            @Container
            @ServiceConnection
            @JvmStatic
            val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:18.4-alpine"))
        }
    }
