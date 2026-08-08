package team.inreok.getiserver.domain.file.service

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockMultipartFile
import team.inreok.getiserver.domain.file.entity.StoredFile
import team.inreok.getiserver.domain.file.entity.type.FilePurpose
import team.inreok.getiserver.domain.file.entity.type.FileStatus
import team.inreok.getiserver.domain.file.exception.FileEmptyException
import team.inreok.getiserver.domain.file.exception.FileStorageException
import team.inreok.getiserver.domain.file.exception.FileTooLargeException
import team.inreok.getiserver.domain.file.exception.FileTypeNotAllowedException
import team.inreok.getiserver.domain.file.exception.InvalidFileNameException
import team.inreok.getiserver.domain.file.exception.MimeMismatchException
import team.inreok.getiserver.domain.file.policy.FileContentTypeValidator
import team.inreok.getiserver.domain.file.policy.FileNameSanitizer
import team.inreok.getiserver.domain.file.policy.FilePolicyProperties
import team.inreok.getiserver.domain.file.policy.FileUploadPolicy
import team.inreok.getiserver.domain.file.service.impl.FileUploadServiceImpl
import team.inreok.getiserver.domain.file.support.InMemoryFileStoragePort
import team.inreok.getiserver.domain.file.support.RecordingStoredFileStateWriter

/**
 * 요구사항 §34의 업로드 Unit Test 목록을 검증한다. 실제 Storage 없이 Fake로 대체하며
 * (`src/test`는 Docker 없이 통과해야 한다) 실제 S3/MinIO 동작은 integrationTest가 검증한다.
 */
class FileUploadServiceTest {
    private val storage = InMemoryFileStoragePort()
    private val stateWriter = RecordingStoredFileStateWriter()

    private val policies =
        FilePolicyProperties(
            policies =
                FilePurpose.entries.associateWith {
                    FileUploadPolicy(
                        extensions = setOf("png", "pdf"),
                        mimeTypes = setOf("image/png", "application/pdf"),
                        maxSizeBytes = MAX_SIZE_BYTES,
                        maxCount = 3,
                    )
                },
        )

    private val service =
        FileUploadServiceImpl(
            storedFileStateWriter = stateWriter,
            fileStoragePort = storage,
            filePolicyProperties = policies,
            fileNameSanitizer = FileNameSanitizer(),
            fileContentTypeValidator = FileContentTypeValidator(),
            fileObjectKeyGenerator = FileObjectKeyGenerator(),
        )

    @Test
    fun `정상 파일은 Storage에 저장되고 Metadata가 UPLOADED로 남는다`() {
        val response = service.upload(MEMBER_ID, pngFile("logo.png"), FilePurpose.PROFILE_IMAGE)

        assertThat(response.originalName).isEqualTo("logo.png")
        assertThat(response.contentType).isEqualTo("image/png")
        assertThat(response.size).isEqualTo(PNG_BYTES.size.toLong())
        assertThat(response.purpose).isEqualTo(FilePurpose.PROFILE_IMAGE)

        val saved = stateWriter.single()
        assertThat(saved.status).isEqualTo(FileStatus.UPLOADED)
        assertThat(saved.uploaderMemberId).isEqualTo(MEMBER_ID)
        assertThat(storage.contains(saved.objectKey)).isTrue()
    }

    @Test
    fun `Storage Key에 원본 파일명이 들어가지 않는다`() {
        service.upload(MEMBER_ID, pngFile("이력서.png"), FilePurpose.PROFILE_IMAGE)

        val objectKey = stateWriter.single().objectKey
        assertThat(objectKey).doesNotContain("이력서")
        assertThat(objectKey).startsWith("PROFILE_IMAGE/")
    }

    @Test
    fun `빈 파일은 거부한다`() {
        val empty = MockMultipartFile("file", "logo.png", "image/png", ByteArray(0))

        assertThatThrownBy { service.upload(MEMBER_ID, empty, FilePurpose.PROFILE_IMAGE) }
            .isInstanceOf(FileEmptyException::class.java)
        assertThat(stateWriter.saved).isEmpty()
    }

    @Test
    fun `최대 크기를 초과하면 거부하고 Storage에 아무것도 남기지 않는다`() {
        val tooLarge =
            MockMultipartFile("file", "logo.png", "image/png", ByteArray((MAX_SIZE_BYTES + 1).toInt()))

        assertThatThrownBy { service.upload(MEMBER_ID, tooLarge, FilePurpose.PROFILE_IMAGE) }
            .isInstanceOf(FileTooLargeException::class.java)
        assertThat(stateWriter.saved).isEmpty()
        assertThat(storage.keys()).isEmpty()
    }

    @Test
    fun `허용되지 않은 확장자는 거부한다`() {
        val exe = MockMultipartFile("file", "malware.exe", "image/png", PNG_BYTES)

        assertThatThrownBy { service.upload(MEMBER_ID, exe, FilePurpose.PROFILE_IMAGE) }
            .isInstanceOf(FileTypeNotAllowedException::class.java)
        assertThat(stateWriter.saved).isEmpty()
    }

    @Test
    fun `확장자와 실제 내용이 다르면 거부한다`() {
        // 이름과 선언 Content-Type은 PDF지만 내용은 PNG다.
        val disguised = MockMultipartFile("file", "resume.pdf", "application/pdf", PNG_BYTES)

        assertThatThrownBy { service.upload(MEMBER_ID, disguised, FilePurpose.JOB_APPLICATION) }
            .isInstanceOf(MimeMismatchException::class.java)
        assertThat(stateWriter.saved).isEmpty()
    }

    @Test
    fun `파일명이 없으면 거부한다`() {
        val noName = MockMultipartFile("file", "", "image/png", PNG_BYTES)

        assertThatThrownBy { service.upload(MEMBER_ID, noName, FilePurpose.PROFILE_IMAGE) }
            .isInstanceOf(InvalidFileNameException::class.java)
    }

    @Test
    fun `파일명은 정규화되어 저장된다`() {
        val traversal = MockMultipartFile("file", "../../secret.png", "image/png", PNG_BYTES)

        val response = service.upload(MEMBER_ID, traversal, FilePurpose.PROFILE_IMAGE)

        assertThat(response.originalName).isEqualTo("secret.png")
    }

    @Test
    fun `클라이언트가 보낸 Content-Type이 아니라 탐지한 형식을 저장한다`() {
        val lying = MockMultipartFile("file", "logo.png", "application/octet-stream", PNG_BYTES)

        val response = service.upload(MEMBER_ID, lying, FilePurpose.PROFILE_IMAGE)

        assertThat(response.contentType).isEqualTo("image/png")
    }

    @Test
    fun `Storage 업로드가 실패하면 FAILED로 남기고 보상 삭제를 수행한다`() {
        storage.failOnUpload = true

        assertThatThrownBy { service.upload(MEMBER_ID, pngFile("logo.png"), FilePurpose.PROFILE_IMAGE) }
            .isInstanceOf(FileStorageException::class.java)

        // PENDING Row는 이미 커밋되어 흔적이 남고, 상태만 FAILED로 전환된다.
        val saved = stateWriter.single()
        assertThat(saved.status).isEqualTo(FileStatus.FAILED)
        assertThat(storage.deleted).contains(saved.objectKey)
    }

    @Test
    fun `보상 삭제까지 실패해도 원래 업로드 실패가 그대로 전달된다`() {
        storage.failOnUpload = true
        storage.failOnDelete = true

        assertThatThrownBy { service.upload(MEMBER_ID, pngFile("logo.png"), FilePurpose.PROFILE_IMAGE) }
            .isInstanceOf(FileStorageException::class.java)

        // 삭제에 실패해도 DB에는 흔적이 남아 Cleanup이 수거할 수 있다.
        assertThat(stateWriter.single().status).isEqualTo(FileStatus.FAILED)
    }

    @Test
    fun `상태 전환이 실패하면 PENDING으로 남아 추적할 수 있다`() {
        stateWriter.failOnMarkUploaded = true

        assertThatThrownBy { service.upload(MEMBER_ID, pngFile("logo.png"), FilePurpose.PROFILE_IMAGE) }
            .isInstanceOf(IllegalStateException::class.java)

        val saved: StoredFile = stateWriter.single()
        assertThat(saved.status).isEqualTo(FileStatus.PENDING)
        // Storage에는 Object가 남지만 DB에 PENDING 흔적이 있어 고아를 찾을 수 있다.
        assertThat(storage.contains(saved.objectKey)).isTrue()
    }

    private fun pngFile(name: String) = MockMultipartFile("file", name, "image/png", PNG_BYTES)

    private companion object {
        private const val MEMBER_ID = 7L
        private const val MAX_SIZE_BYTES = 1024L

        private val PNG_BYTES =
            byteArrayOf(
                0x89.toByte(),
                0x50,
                0x4E,
                0x47,
                0x0D,
                0x0A,
                0x1A,
                0x0A,
                0x00,
                0x00,
                0x00,
                0x0D,
                0x49,
                0x48,
                0x44,
                0x52,
                0x00,
                0x00,
                0x00,
                0x01,
                0x00,
                0x00,
                0x00,
                0x01,
                0x08,
                0x06,
                0x00,
                0x00,
                0x00,
                0x1F,
                0x15,
                0xC4.toByte(),
                0x89.toByte(),
            )
    }
}
