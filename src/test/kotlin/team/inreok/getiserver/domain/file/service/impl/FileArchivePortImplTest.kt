package team.inreok.getiserver.domain.file.service.impl

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import team.inreok.getiserver.domain.file.archive.FileArchiveEntry
import team.inreok.getiserver.domain.file.archive.FileArchiveProperties
import team.inreok.getiserver.domain.file.entity.StoredFile
import team.inreok.getiserver.domain.file.entity.type.FilePurpose
import team.inreok.getiserver.domain.file.exception.FileArchiveEmptyException
import team.inreok.getiserver.domain.file.exception.FileArchiveTooLargeException
import team.inreok.getiserver.domain.file.repository.StoredFileRepository
import team.inreok.getiserver.domain.file.support.InMemoryFileStoragePort
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.zip.ZipInputStream

/**
 * `FileArchivePort`의 4가지 책임(§23) -- 권한 검증된 목록 조회(존재하지 않거나 보이지 않는 File
 * 건너뛰기), Storage Stream 획득, ZIP 생성(Streaming), Archive 파일명 안전화 -- 을 검증한다.
 */
@ExtendWith(MockitoExtension::class)
class FileArchivePortImplTest {
    @Mock
    private lateinit var storedFileRepository: StoredFileRepository

    private val storage = InMemoryFileStoragePort()
    private val properties = FileArchiveProperties(maxFileCount = 3, maxTotalSizeBytes = 1000)
    private val port: FileArchivePortImpl by lazy { FileArchivePortImpl(storedFileRepository, storage, properties) }

    @Test
    fun `정상 파일들을 하나의 ZIP으로 묶는다`() {
        val fileA = storedFileOf(id = 1L, name = "resume.pdf", content = "A".repeat(10).toByteArray())
        val fileB = storedFileOf(id = 2L, name = "portfolio.pdf", content = "B".repeat(10).toByteArray())
        givenFiles(fileA, fileB)
        val output = ByteArrayOutputStream()

        val result =
            port.writeZip(
                listOf(FileArchiveEntry(1L, "resume.pdf"), FileArchiveEntry(2L, "portfolio.pdf")),
                output,
            )

        assertThat(result.includedFileIds).containsExactly(1L, 2L)
        assertThat(result.skippedFileIds).isEmpty()
        val entries = readZipEntries(output)
        assertThat(entries.keys).containsExactlyInAnyOrder("resume.pdf", "portfolio.pdf")
        assertThat(entries["resume.pdf"]).isEqualTo("A".repeat(10).toByteArray())
    }

    @Test
    fun `존재하지 않거나 보이지 않는 파일은 건너뛰고 나머지는 담는다`() {
        val visible = storedFileOf(id = 1L, name = "a.pdf", content = "A".toByteArray())
        val pending = pendingFileOf(id = 2L, name = "b.pdf", sizeBytes = 1)
        given(storedFileRepository.findAllByIdIn(setOf(1L, 2L, 999L))).willReturn(listOf(visible, pending))
        val output = ByteArrayOutputStream()

        val result =
            port.writeZip(
                listOf(
                    FileArchiveEntry(1L, "a.pdf"),
                    FileArchiveEntry(2L, "b.pdf"),
                    FileArchiveEntry(999L, "missing.pdf"),
                ),
                output,
            )

        assertThat(result.includedFileIds).containsExactly(1L)
        // 원래 요청 순서를 보존한다.
        assertThat(result.skippedFileIds).containsExactly(2L, 999L)
    }

    @Test
    fun `Storage 읽기에 실패한 파일만 건너뛰고 나머지는 계속 담는다`() {
        val ok = storedFileOf(id = 1L, name = "ok.pdf", content = "OK".toByteArray())
        val broken = storedFileOf(id = 2L, name = "broken.pdf", content = "X".toByteArray())
        givenFiles(ok, broken)
        storage.failOnDownloadKeys = setOf(broken.objectKey)
        val output = ByteArrayOutputStream()

        val result =
            port.writeZip(listOf(FileArchiveEntry(1L, "ok.pdf"), FileArchiveEntry(2L, "broken.pdf")), output)

        assertThat(result.includedFileIds).containsExactly(1L)
        assertThat(result.skippedFileIds).containsExactly(2L)
        assertThat(readZipEntries(output).keys).containsExactly("ok.pdf")
    }

    @Test
    fun `같은 이름이 겹치면 번호를 붙인다`() {
        val fileA = storedFileOf(id = 1L, name = "photo.jpg", content = "1".toByteArray())
        val fileB = storedFileOf(id = 2L, name = "photo.jpg", content = "2".toByteArray())
        givenFiles(fileA, fileB)
        val output = ByteArrayOutputStream()

        port.writeZip(listOf(FileArchiveEntry(1L, "photo.jpg"), FileArchiveEntry(2L, "photo.jpg")), output)

        assertThat(readZipEntries(output).keys).containsExactlyInAnyOrder("photo.jpg", "photo(2).jpg")
    }

    @Test
    fun `Entry 이름의 경로 구분자는 제거된다(ZIP Slip 방지)`() {
        val file = storedFileOf(id = 1L, name = "secret.pdf", content = "S".toByteArray())
        givenFiles(file)
        val output = ByteArrayOutputStream()

        port.writeZip(listOf(FileArchiveEntry(1L, "../../secret.pdf")), output)

        assertThat(readZipEntries(output).keys).containsExactly(".._.._secret.pdf")
    }

    @Test
    fun `개수 상한을 넘으면 Storage 조회 없이 즉시 거부한다`() {
        val entries = (1L..4L).map { FileArchiveEntry(it, "f$it.pdf") }

        assertThatThrownBy { port.writeZip(entries, ByteArrayOutputStream()) }
            .isInstanceOf(FileArchiveTooLargeException::class.java)
    }

    @Test
    fun `총 용량 상한을 넘으면 거부한다`() {
        val big = storedFileOf(id = 1L, name = "big.pdf", content = ByteArray(BIG_FILE_SIZE))
        givenFiles(big)

        assertThatThrownBy {
            port.writeZip(listOf(FileArchiveEntry(1L, "big.pdf")), ByteArrayOutputStream())
        }.isInstanceOf(FileArchiveTooLargeException::class.java)
    }

    @Test
    fun `포함할 파일이 하나도 없으면 예외를 던진다`() {
        given(storedFileRepository.findAllByIdIn(setOf(999L))).willReturn(emptyList())

        assertThatThrownBy {
            port.writeZip(listOf(FileArchiveEntry(999L, "missing.pdf")), ByteArrayOutputStream())
        }.isInstanceOf(FileArchiveEmptyException::class.java)
    }

    private fun givenFiles(vararg files: StoredFile) {
        val ids = files.mapNotNull { it.id }.toSet()
        given(storedFileRepository.findAllByIdIn(ids)).willReturn(files.toList())
    }

    private fun storedFileOf(
        id: Long,
        name: String,
        content: ByteArray,
    ): StoredFile =
        pendingFileOf(id, name, content.size.toLong()).apply {
            markUploaded()
            storage.upload(objectKey, contentType, content.size.toLong(), content.inputStream())
        }

    private fun pendingFileOf(
        id: Long,
        name: String,
        sizeBytes: Long,
    ): StoredFile =
        StoredFile(
            purpose = FilePurpose.JOB_APPLICATION,
            objectKey = "JOB_APPLICATION/2026/08/${UUID.randomUUID()}",
            originalName = name,
            contentType = "application/pdf",
            sizeBytes = sizeBytes,
            uploaderMemberId = 1L,
            extension = "pdf",
        ).apply { this.id = id }

    private fun readZipEntries(output: ByteArrayOutputStream): Map<String, ByteArray> {
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(output.toByteArray().inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                entries[entry.name] = zip.readBytes()
                entry = zip.nextEntry
            }
        }
        return entries
    }

    private companion object {
        /** properties.maxTotalSizeBytes(1000)보다 커야 한다. */
        const val BIG_FILE_SIZE = 1500
    }
}
