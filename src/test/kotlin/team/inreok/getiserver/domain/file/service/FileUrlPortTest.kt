package team.inreok.getiserver.domain.file.service

import org.assertj.core.api.Assertions.assertThat
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
import team.inreok.getiserver.domain.file.policy.FileDispositionPolicy
import team.inreok.getiserver.domain.file.repository.StoredFileRepository
import team.inreok.getiserver.domain.file.service.impl.FileUrlPortImpl
import team.inreok.getiserver.domain.file.storage.FileStorageProperties
import team.inreok.getiserver.domain.file.support.InMemoryFileStoragePort
import java.time.LocalDateTime
import java.util.UUID

/**
 * 이미지 Presigned URL 발급은 다운로드 API와 **같은 권한 규칙**을 따라야 한다. 여기서 검사가
 * 빠지면 각 Domain이 등록한 [FileAccessChecker]가 이 Port로 우회되어, 예를 들어 비공개 프로필
 * 이미지의 URL이 회원 목록 응답으로 그대로 나간다(요구사항 §14).
 */
@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FileUrlPortTest {
    @Mock
    private lateinit var storedFileRepository: StoredFileRepository

    private val storage = InMemoryFileStoragePort()

    private val properties =
        FileStorageProperties(bucket = "geti-test", region = "us-east-1", accessKey = "a", secretKey = "b")

    /** 공개 프로필(PUBLIC_MEMBER_ID)만 남에게 보여 주는 Checker. #86이 등록할 규칙의 축소판이다. */
    private val memberChecker =
        object : FileAccessChecker {
            override val ownerType = FileOwnerType.MEMBER

            override fun canDownload(
                requesterId: Long,
                ownerId: Long,
            ): Boolean = ownerId == PUBLIC_MEMBER_ID
        }

    private fun portWith(checkers: List<FileAccessChecker>) =
        FileUrlPortImpl(
            storedFileRepository = storedFileRepository,
            fileAccessResolver = FileAccessResolver(checkers),
            fileStoragePort = storage,
            fileDispositionPolicy = FileDispositionPolicy(),
            fileStorageProperties = properties,
        )

    @Test
    fun `업로더 본인은 자기 이미지 URL을 받는다`() {
        givenFiles(uploadedImage(uploaderMemberId = OWNER))

        val urls = portWith(emptyList()).presignedImageUrls(OWNER, listOf(FILE_ID))

        assertThat(urls).containsOnlyKeys(FILE_ID)
        assertThat(urls.getValue(FILE_ID)).contains("inline")
    }

    @Test
    fun `다른 사용자는 아직 연결되지 않은 이미지의 URL을 받지 못한다`() {
        // 연결 전 이미지는 위임할 리소스가 없어 업로더만 접근할 수 있다. fileId를 알아냈다는
        // 것만으로 서명된 URL이 나가면 안 된다.
        givenFiles(uploadedImage(uploaderMemberId = OWNER))

        val urls = portWith(listOf(memberChecker)).presignedImageUrls(OTHER, listOf(FILE_ID))

        assertThat(urls).isEmpty()
    }

    @Test
    fun `연결된 이미지는 담당 Domain의 Checker가 허용할 때만 URL이 나간다`() {
        givenFiles(linkedImage(uploaderMemberId = OWNER, ownerId = PUBLIC_MEMBER_ID))

        val urls = portWith(listOf(memberChecker)).presignedImageUrls(OTHER, listOf(FILE_ID))

        assertThat(urls).containsOnlyKeys(FILE_ID)
    }

    @Test
    fun `Checker가 거부한 비공개 이미지는 결과에서 빠진다`() {
        givenFiles(linkedImage(uploaderMemberId = OWNER, ownerId = PRIVATE_MEMBER_ID))

        val urls = portWith(listOf(memberChecker)).presignedImageUrls(OTHER, listOf(FILE_ID))

        assertThat(urls).isEmpty()
    }

    @Test
    fun `Checker가 등록되지 않은 리소스 종류는 기본 거부다`() {
        givenFiles(linkedImage(uploaderMemberId = OWNER, ownerId = PUBLIC_MEMBER_ID))

        val urls = portWith(emptyList()).presignedImageUrls(OTHER, listOf(FILE_ID))

        assertThat(urls).isEmpty()
    }

    @Test
    fun `이미지 목적이 아닌 파일은 결과에서 빠진다`() {
        // 문서는 다운로드 API를 거쳐야 한다. 권한이 있어도 이 Port로는 나가지 않는다.
        givenFiles(
            uploadedImage(
                uploaderMemberId = OWNER,
                purpose = FilePurpose.JOB_APPLICATION,
                contentType = "application/pdf",
            ),
        )

        val urls = portWith(emptyList()).presignedImageUrls(OWNER, listOf(FILE_ID))

        assertThat(urls).isEmpty()
    }

    @Test
    fun `업로드가 끝나지 않은 이미지는 결과에서 빠진다`() {
        givenFiles(pendingImage(uploaderMemberId = OWNER))

        val urls = portWith(emptyList()).presignedImageUrls(OWNER, listOf(FILE_ID))

        assertThat(urls).isEmpty()
    }

    @Test
    fun `빈 목록을 넘기면 Repository를 조회하지 않는다`() {
        assertThat(portWith(emptyList()).presignedImageUrls(OWNER, emptyList())).isEmpty()
    }

    private fun givenFiles(vararg files: StoredFile) {
        given(storedFileRepository.findAllByIdIn(setOf(FILE_ID))).willReturn(files.toList())
    }

    private fun pendingImage(
        uploaderMemberId: Long,
        purpose: FilePurpose = FilePurpose.PROFILE_IMAGE,
        contentType: String = "image/png",
    ) = StoredFile(
        purpose = purpose,
        objectKey = "$purpose/2026/08/${UUID.randomUUID()}",
        originalName = "profile.png",
        contentType = contentType,
        sizeBytes = 100,
        uploaderMemberId = uploaderMemberId,
        extension = "png",
    ).apply { id = FILE_ID }

    private fun uploadedImage(
        uploaderMemberId: Long,
        purpose: FilePurpose = FilePurpose.PROFILE_IMAGE,
        contentType: String = "image/png",
    ) = pendingImage(uploaderMemberId, purpose, contentType).apply { markUploaded() }

    private fun linkedImage(
        uploaderMemberId: Long,
        ownerId: Long,
    ) = uploadedImage(uploaderMemberId).apply {
        linkTo(FileOwnerType.MEMBER, ownerId, LocalDateTime.now())
    }

    private companion object {
        private const val FILE_ID = 42L
        private const val OWNER = 1L
        private const val OTHER = 2L
        private const val PUBLIC_MEMBER_ID = 10L
        private const val PRIVATE_MEMBER_ID = 11L
    }
}
