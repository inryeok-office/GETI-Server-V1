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
import team.inreok.getiserver.domain.file.entity.StoredFile
import team.inreok.getiserver.domain.file.entity.type.FileOwnerType
import team.inreok.getiserver.domain.file.entity.type.FilePurpose
import team.inreok.getiserver.domain.file.entity.type.FileStatus
import team.inreok.getiserver.domain.file.exception.FileAlreadyLinkedException
import team.inreok.getiserver.domain.file.exception.FileCountExceededException
import team.inreok.getiserver.domain.file.exception.FileNotFoundException
import team.inreok.getiserver.domain.file.exception.FileNotOwnedException
import team.inreok.getiserver.domain.file.exception.FilePurposeMismatchException
import team.inreok.getiserver.domain.file.policy.FilePolicyProperties
import team.inreok.getiserver.domain.file.policy.FileUploadPolicy
import team.inreok.getiserver.domain.file.repository.StoredFileRepository
import team.inreok.getiserver.domain.file.service.impl.FileLinkPortImpl
import team.inreok.getiserver.global.error.BusinessException
import java.time.LocalDateTime
import java.util.UUID

/**
 * 요구사항 §12/§14/§24. 핵심은 "File ID는 권한 증명이 아니다" -- 남의 파일 ID를 알아도 자기
 * 리소스에 붙일 수 없어야 한다.
 */
@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FileLinkPortTest {
    @Mock
    private lateinit var storedFileRepository: StoredFileRepository

    private val policies =
        FilePolicyProperties(
            policies =
                FilePurpose.entries.associateWith {
                    FileUploadPolicy(
                        extensions = setOf("png", "pdf"),
                        mimeTypes = setOf("image/png", "application/pdf"),
                        maxSizeBytes = 1024,
                        maxCount = MAX_COUNT,
                    )
                },
        )

    private val port: FileLinkPortImpl by lazy { FileLinkPortImpl(storedFileRepository, policies) }

    @Test
    fun `본인이 올린 파일은 연결된다`() {
        val file = uploadedFile(id = 1L, uploaderMemberId = OWNER)
        givenFiles(setOf(1L), file)
        givenLinkedCount(0)

        val snapshots = port.validateAndLink(OWNER, listOf(1L), FilePurpose.JOB_APPLICATION, APPLICATION_ID)

        assertThat(snapshots).hasSize(1)
        assertThat(snapshots.single().fileId).isEqualTo(1L)
        assertThat(file.status).isEqualTo(FileStatus.LINKED)
        assertThat(file.ownerType).isEqualTo(FileOwnerType.JOB_APPLICATION)
        assertThat(file.ownerId).isEqualTo(APPLICATION_ID)
        assertThat(file.linkedAt).isNotNull()
    }

    @Test
    fun `Snapshot에는 Storage 내부 정보가 들어가지 않는다`() {
        val file = uploadedFile(id = 1L, uploaderMemberId = OWNER)
        givenFiles(setOf(1L), file)
        givenLinkedCount(0)

        val snapshot =
            port.validateAndLink(OWNER, listOf(1L), FilePurpose.JOB_APPLICATION, APPLICATION_ID).single()

        // FileSnapshot에는 objectKey를 담을 Field 자체가 없다. 값이 새지 않는지 확인한다.
        assertThat(snapshot.toString()).doesNotContain(file.objectKey)
    }

    @Test
    fun `다른 사용자가 올린 파일은 연결할 수 없다`() {
        val othersFile = uploadedFile(id = 1L, uploaderMemberId = OTHER)
        givenFiles(setOf(1L), othersFile)

        assertThatThrownBy {
            port.validateAndLink(OWNER, listOf(1L), FilePurpose.JOB_APPLICATION, APPLICATION_ID)
        }.isInstanceOf(FileNotOwnedException::class.java)

        assertThat(othersFile.status).isEqualTo(FileStatus.UPLOADED)
    }

    @Test
    fun `Purpose가 다르면 연결할 수 없다`() {
        givenFiles(setOf(1L), uploadedFile(id = 1L, uploaderMemberId = OWNER, purpose = FilePurpose.PROFILE_IMAGE))

        assertThatThrownBy {
            port.validateAndLink(OWNER, listOf(1L), FilePurpose.JOB_APPLICATION, APPLICATION_ID)
        }.isInstanceOf(FilePurposeMismatchException::class.java)
    }

    @Test
    fun `이미 연결된 파일은 다시 연결할 수 없다`() {
        val file = uploadedFile(id = 1L, uploaderMemberId = OWNER)
        file.linkTo(FileOwnerType.JOB_APPLICATION, 999L, LocalDateTime.now())
        givenFiles(setOf(1L), file)

        assertThatThrownBy {
            port.validateAndLink(OWNER, listOf(1L), FilePurpose.JOB_APPLICATION, APPLICATION_ID)
        }.isInstanceOf(FileAlreadyLinkedException::class.java)
    }

    @Test
    fun `존재하지 않는 파일은 FILE_NOT_FOUND다`() {
        givenFiles(setOf(404L))

        assertThatThrownBy {
            port.validateAndLink(OWNER, listOf(404L), FilePurpose.JOB_APPLICATION, APPLICATION_ID)
        }.isInstanceOf(FileNotFoundException::class.java)
    }

    @Test
    fun `업로드가 끝나지 않은 파일은 존재하지 않는 것으로 다룬다`() {
        // PENDING은 Storage 업로드가 아직 끝나지 않은 상태다. 남의 파일 ID를 넣어보며 존재
        // 여부를 알아내지 못하도록 FILE_NOT_FOUND로 통일한다.
        givenFiles(setOf(1L), pendingFile(id = 1L, uploaderMemberId = OWNER))

        assertThatThrownBy {
            port.validateAndLink(OWNER, listOf(1L), FilePurpose.JOB_APPLICATION, APPLICATION_ID)
        }.isInstanceOf(FileNotFoundException::class.java)
    }

    @Test
    fun `중복된 File ID를 전달하면 거부한다`() {
        // 중복 검사는 Repository 조회 전에 수행되므로 stub이 필요 없다.

        assertThatThrownBy {
            port.validateAndLink(OWNER, listOf(1L, 1L), FilePurpose.JOB_APPLICATION, APPLICATION_ID)
        }.isInstanceOf(BusinessException::class.java)
    }

    @Test
    fun `목적별 최대 개수를 넘으면 거부한다`() {
        val files = (1L..MAX_COUNT + 1L).map { uploadedFile(id = it, uploaderMemberId = OWNER) }
        givenFiles(files.map { it.id!! }.toSet(), *files.toTypedArray())
        givenLinkedCount(0)

        assertThatThrownBy {
            port.validateAndLink(OWNER, files.map { it.id!! }, FilePurpose.JOB_APPLICATION, APPLICATION_ID)
        }.isInstanceOf(FileCountExceededException::class.java)

        assertThat(files).allMatch { it.status == FileStatus.UPLOADED }
    }

    @Test
    fun `이미 연결된 개수까지 합산해 상한을 검사한다`() {
        givenFiles(setOf(1L), uploadedFile(id = 1L, uploaderMemberId = OWNER))
        givenLinkedCount(MAX_COUNT.toLong())

        assertThatThrownBy {
            port.validateAndLink(OWNER, listOf(1L), FilePurpose.JOB_APPLICATION, APPLICATION_ID)
        }.isInstanceOf(FileCountExceededException::class.java)
    }

    @Test
    fun `연결 해제하면 다시 연결 가능한 상태로 돌아간다`() {
        val file = uploadedFile(id = 1L, uploaderMemberId = OWNER)
        file.linkTo(FileOwnerType.JOB_APPLICATION, APPLICATION_ID, LocalDateTime.now())
        given(
            storedFileRepository.findAllByOwnerTypeAndOwnerIdAndStatus(
                FileOwnerType.JOB_APPLICATION,
                APPLICATION_ID,
                FileStatus.LINKED,
            ),
        ).willReturn(listOf(file))

        port.unlinkAllOf(FileOwnerType.JOB_APPLICATION, APPLICATION_ID)

        assertThat(file.status).isEqualTo(FileStatus.UPLOADED)
        assertThat(file.ownerType).isNull()
        assertThat(file.ownerId).isNull()
        assertThat(file.linkedAt).isNull()
    }

    @Test
    fun `연결된 파일이 없어도 연결 해제는 오류가 아니다`() {
        given(
            storedFileRepository.findAllByOwnerTypeAndOwnerIdAndStatus(
                FileOwnerType.JOB_APPLICATION,
                APPLICATION_ID,
                FileStatus.LINKED,
            ),
        ).willReturn(emptyList())

        port.unlinkAllOf(FileOwnerType.JOB_APPLICATION, APPLICATION_ID)
    }

    @Test
    fun `빈 목록을 연결하면 아무 일도 일어나지 않는다`() {
        assertThat(port.validateAndLink(OWNER, emptyList(), FilePurpose.JOB_APPLICATION, APPLICATION_ID)).isEmpty()
    }

    @Test
    fun `linkedFilesOf 배치 조회는 ownerId별로 파일을 묶어 돌려준다`() {
        val fileForFirst =
            uploadedFile(id = 1L, uploaderMemberId = OWNER, purpose = FilePurpose.INQUIRY_ANSWER_ATTACHMENT)
        fileForFirst.linkTo(FileOwnerType.INQUIRY_ANSWER, 10L, LocalDateTime.now())
        val fileForSecond =
            uploadedFile(id = 2L, uploaderMemberId = OWNER, purpose = FilePurpose.INQUIRY_ANSWER_ATTACHMENT)
        fileForSecond.linkTo(FileOwnerType.INQUIRY_ANSWER, 20L, LocalDateTime.now())
        given(
            storedFileRepository.findAllByOwnerTypeAndOwnerIdInAndStatus(
                FileOwnerType.INQUIRY_ANSWER,
                setOf(10L, 20L),
                FileStatus.LINKED,
            ),
        ).willReturn(listOf(fileForFirst, fileForSecond))

        val filesByOwnerId = port.linkedFilesOf(FileOwnerType.INQUIRY_ANSWER, listOf(10L, 20L))

        assertThat(filesByOwnerId).hasSize(2)
        assertThat(filesByOwnerId.getValue(10L).single().fileId).isEqualTo(1L)
        assertThat(filesByOwnerId.getValue(20L).single().fileId).isEqualTo(2L)
    }

    @Test
    fun `linkedFilesOf 배치 조회는 ownerId가 비어있으면 조회 없이 빈 Map을 돌려준다`() {
        assertThat(port.linkedFilesOf(FileOwnerType.INQUIRY_ANSWER, emptyList())).isEmpty()
    }

    @Test
    fun `snapshotsOf는 보이지 않는 상태의 파일을 제외한다`() {
        val visible = uploadedFile(id = 1L, uploaderMemberId = OWNER)
        val pending = pendingFile(id = 2L, uploaderMemberId = OWNER)
        given(storedFileRepository.findAllByIdIn(setOf(1L, 2L))).willReturn(listOf(visible, pending))

        val snapshots = port.snapshotsOf(listOf(1L, 2L))

        assertThat(snapshots.keys).containsExactly(1L)
    }

    /**
     * Mockito Matcher 대신 실제로 전달될 ID 집합으로 stub한다(mockito-kotlin 의존성이 없다).
     *
     * `findAllByIdIn`이 아니라 Lock을 거는 `findAllByIdInForUpdate`로 stub한다. 연결 경로가 Lock
     * 없는 조회로 되돌아가면 stub이 걸리지 않아 빈 목록이 반환되고, 이 Class의 Test들이
     * `FileNotFoundException`으로 깨진다 -- 동시성 방어가 사라진 것을 알아채는 장치다.
     */
    private fun givenFiles(
        ids: Set<Long>,
        vararg files: StoredFile,
    ) {
        given(storedFileRepository.findAllByIdInForUpdate(ids)).willReturn(files.toList())
    }

    private fun givenLinkedCount(count: Long) {
        given(
            storedFileRepository.countByOwnerTypeAndOwnerIdAndPurposeAndStatus(
                FileOwnerType.JOB_APPLICATION,
                APPLICATION_ID,
                FilePurpose.JOB_APPLICATION,
                FileStatus.LINKED,
            ),
        ).willReturn(count)
    }

    private fun pendingFile(
        id: Long,
        uploaderMemberId: Long,
        purpose: FilePurpose = FilePurpose.JOB_APPLICATION,
    ) = StoredFile(
        purpose = purpose,
        objectKey = "$purpose/2026/08/${UUID.randomUUID()}",
        originalName = "file.png",
        contentType = "image/png",
        sizeBytes = 100,
        uploaderMemberId = uploaderMemberId,
        extension = "png",
    ).apply { this.id = id }

    private fun uploadedFile(
        id: Long,
        uploaderMemberId: Long,
        purpose: FilePurpose = FilePurpose.JOB_APPLICATION,
    ) = pendingFile(id, uploaderMemberId, purpose).apply { markUploaded() }

    private companion object {
        private const val OWNER = 1L
        private const val OTHER = 2L
        private const val APPLICATION_ID = 55L
        private const val MAX_COUNT = 3
    }
}
