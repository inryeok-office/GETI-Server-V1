package team.inreok.getiserver.domain.file.service

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import team.inreok.getiserver.domain.file.access.FileAccessChecker
import team.inreok.getiserver.domain.file.access.FileAccessResolver
import team.inreok.getiserver.domain.file.entity.StoredFile
import team.inreok.getiserver.domain.file.entity.type.FileOwnerType
import team.inreok.getiserver.domain.file.entity.type.FilePurpose
import team.inreok.getiserver.domain.file.exception.FileAccessDeniedException
import team.inreok.getiserver.domain.file.exception.FileNotFoundException
import team.inreok.getiserver.domain.file.policy.FileDispositionPolicy
import team.inreok.getiserver.domain.file.repository.StoredFileRepository
import team.inreok.getiserver.domain.file.service.impl.FileDownloadServiceImpl
import team.inreok.getiserver.domain.file.storage.FileStorageProperties
import team.inreok.getiserver.domain.file.support.InMemoryFileStoragePort
import java.time.LocalDateTime
import java.util.Optional
import java.util.UUID

/**
 * 요구사항 §14/§15/§16. 인증만으로는 부족하고 파일별 접근 권한이 필요하다는 것이 핵심이다.
 */
@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FileDownloadServiceTest {
    @Mock
    private lateinit var storedFileRepository: StoredFileRepository

    private val storage = InMemoryFileStoragePort()

    private val properties =
        FileStorageProperties(bucket = "geti-test", region = "us-east-1", accessKey = "a", secretKey = "b")

    /** JOB_APPLICATION만 판정할 수 있고, 담당자(TEACHER_ID)에게만 허용하는 Checker. */
    private val applicationChecker =
        object : FileAccessChecker {
            override val ownerType = FileOwnerType.JOB_APPLICATION

            override fun canDownload(
                requesterId: Long,
                ownerId: Long,
            ): Boolean = requesterId == TEACHER_ID
        }

    private fun serviceWith(checkers: List<FileAccessChecker>) =
        FileDownloadServiceImpl(
            storedFileRepository = storedFileRepository,
            fileAccessResolver = FileAccessResolver(checkers),
            fileDispositionPolicy = FileDispositionPolicy(),
            fileStoragePort = storage,
            fileStorageProperties = properties,
        )

    @Test
    fun `업로더 본인은 미연결 파일도 받을 수 있다`() {
        givenFile(uploadedFile(uploaderMemberId = OWNER))

        val url = serviceWith(emptyList()).createDownloadUrl(OWNER, FILE_ID, inlineRequested = false)

        assertThat(url.toString()).contains("attachment")
    }

    @Test
    fun `다른 사용자는 미연결 파일을 받을 수 없다`() {
        // §14 "다른 사용자의 미연결 파일 다운로드" 방어. 연결되지 않은 파일은 위임할 리소스가
        // 없으므로 업로더 외에는 누구도 접근할 수 없다.
        givenFile(uploadedFile(uploaderMemberId = OWNER))

        assertThatThrownBy {
            serviceWith(emptyList()).createDownloadUrl(OTHER, FILE_ID, inlineRequested = false)
        }.isInstanceOf(FileAccessDeniedException::class.java)
    }

    @Test
    fun `연결된 파일은 담당 도메인의 Checker가 허용하면 받을 수 있다`() {
        givenFile(linkedFile(uploaderMemberId = OWNER))

        val url =
            serviceWith(listOf(applicationChecker)).createDownloadUrl(TEACHER_ID, FILE_ID, inlineRequested = false)

        assertThat(url).isNotNull()
    }

    @Test
    fun `Checker가 거부하면 403이다`() {
        givenFile(linkedFile(uploaderMemberId = OWNER))

        assertThatThrownBy {
            serviceWith(listOf(applicationChecker)).createDownloadUrl(OTHER, FILE_ID, inlineRequested = false)
        }.isInstanceOf(FileAccessDeniedException::class.java)
    }

    @Test
    fun `Checker가 등록되지 않은 리소스 종류는 기본 거부다`() {
        // 아직 파일 첨부를 구현하지 않은 Domain의 파일이 실수로 노출되지 않게 한다.
        givenFile(linkedFile(uploaderMemberId = OWNER))

        assertThatThrownBy {
            serviceWith(emptyList()).createDownloadUrl(TEACHER_ID, FILE_ID, inlineRequested = false)
        }.isInstanceOf(FileAccessDeniedException::class.java)
    }

    @Test
    fun `존재하지 않는 파일은 404다`() {
        given(storedFileRepository.findById(FILE_ID)).willReturn(Optional.empty())

        assertThatThrownBy {
            serviceWith(emptyList()).createDownloadUrl(OWNER, FILE_ID, inlineRequested = false)
        }.isInstanceOf(FileNotFoundException::class.java)
    }

    @Test
    fun `업로드가 끝나지 않은 파일은 업로더에게도 404다`() {
        // PENDING은 Storage에 Object가 없을 수 있다. 권한 문제가 아니라 없는 파일로 다룬다.
        givenFile(pendingFile(uploaderMemberId = OWNER))

        assertThatThrownBy {
            serviceWith(emptyList()).createDownloadUrl(OWNER, FILE_ID, inlineRequested = false)
        }.isInstanceOf(FileNotFoundException::class.java)
    }

    @Test
    fun `이미지는 inline 요청이 반영된다`() {
        givenFile(uploadedFile(uploaderMemberId = OWNER, contentType = "image/png"))

        val url = serviceWith(emptyList()).createDownloadUrl(OWNER, FILE_ID, inlineRequested = true)

        assertThat(url.toString()).contains("inline")
    }

    @Test
    fun `PDF는 inline을 요청해도 attachment로 내려간다`() {
        givenFile(uploadedFile(uploaderMemberId = OWNER, contentType = "application/pdf"))

        val url = serviceWith(emptyList()).createDownloadUrl(OWNER, FILE_ID, inlineRequested = true)

        assertThat(url.toString()).contains("attachment")
    }

    private fun givenFile(file: StoredFile) {
        given(storedFileRepository.findById(FILE_ID)).willReturn(Optional.of(file))
    }

    private fun pendingFile(
        uploaderMemberId: Long,
        contentType: String = "image/png",
    ) = StoredFile(
        purpose = FilePurpose.JOB_APPLICATION,
        objectKey = "JOB_APPLICATION/2026/08/${UUID.randomUUID()}",
        originalName = "이력서.png",
        contentType = contentType,
        sizeBytes = 100,
        uploaderMemberId = uploaderMemberId,
        extension = "png",
    ).apply { id = FILE_ID }

    private fun uploadedFile(
        uploaderMemberId: Long,
        contentType: String = "image/png",
    ) = pendingFile(uploaderMemberId, contentType).apply { markUploaded() }

    private fun linkedFile(uploaderMemberId: Long) =
        uploadedFile(uploaderMemberId).apply {
            linkTo(FileOwnerType.JOB_APPLICATION, APPLICATION_ID, LocalDateTime.now())
        }

    private companion object {
        private const val FILE_ID = 42L
        private const val OWNER = 1L
        private const val OTHER = 2L
        private const val TEACHER_ID = 3L
        private const val APPLICATION_ID = 55L
    }
}
