package team.inreok.getiserver.domain.file.service.impl

import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Service
import team.inreok.getiserver.domain.file.archive.FileArchiveContentEntry
import team.inreok.getiserver.domain.file.archive.FileArchiveEntry
import team.inreok.getiserver.domain.file.archive.FileArchivePort
import team.inreok.getiserver.domain.file.archive.FileArchiveProperties
import team.inreok.getiserver.domain.file.archive.FileArchiveResult
import team.inreok.getiserver.domain.file.entity.StoredFile
import team.inreok.getiserver.domain.file.exception.FileArchiveEmptyException
import team.inreok.getiserver.domain.file.exception.FileArchiveTooLargeException
import team.inreok.getiserver.domain.file.exception.FileStorageException
import team.inreok.getiserver.domain.file.repository.StoredFileRepository
import team.inreok.getiserver.domain.file.storage.FileStoragePort
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * [FileArchivePort]의 구현이다(Issue #154, Epic #84 Phase 6). DB 조회([StoredFileRepository.findAllByIdIn],
 * 단건 호출이라 Spring Data JPA 기본 Transaction으로 충분하다)와 Storage Streaming을 분리한다 --
 * `download`로 시작하는 실제 파일 전송은 느린 외부 I/O라 `@Transactional` 안에서 하지 않는다
 * (`.claude/rules/spring-boot.md`의 Transaction Convention).
 */
@Service
@EnableConfigurationProperties(FileArchiveProperties::class)
class FileArchivePortImpl(
    private val storedFileRepository: StoredFileRepository,
    private val fileStoragePort: FileStoragePort,
    private val fileArchiveProperties: FileArchiveProperties,
) : FileArchivePort {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun writeZip(
        entries: List<FileArchiveEntry>,
        outputStream: OutputStream,
    ): FileArchiveResult = writeZip(entries, emptyList(), outputStream)

    override fun writeZip(
        entries: List<FileArchiveEntry>,
        contentEntries: List<FileArchiveContentEntry>,
        outputStream: OutputStream,
    ): FileArchiveResult {
        validateEntryCount(entries.size + contentEntries.size)

        // FileLinkPortImpl.snapshotsOf와 같은 이유로 빈 목록이면 조회 자체를 생략한다.
        val filesById =
            if (entries.isEmpty()) {
                emptyMap()
            } else {
                storedFileRepository.findAllByIdIn(entries.map { it.fileId }.toSet()).associateBy { it.id }
            }
        val visibleEntries = entries.filter { filesById[it.fileId]?.isVisible() == true }
        // 존재하지 않거나 보이지 않는 상태의 fileId는 FileLinkPort.snapshotsOf와 같은 방식으로
        // 조용히 건너뛴다(§23) -- List - Set은 원래 순서를 보존한다.
        val notVisibleFileIds = entries.map { it.fileId } - visibleEntries.map { it.fileId }.toSet()
        if (visibleEntries.isEmpty() && contentEntries.isEmpty()) throw FileArchiveEmptyException()

        validateTotalSize(
            visibleEntries.sumOf { filesById.getValue(it.fileId).sizeBytes } +
                contentEntries.sumOf { it.content.size.toLong() },
        )

        val includedFileIds = mutableListOf<Long>()
        val failedFileIds = mutableListOf<Long>()
        val usedEntryNames = mutableSetOf<String>()

        // UTF-8로 명시해야 한글 파일명이 Windows 탐색기/macOS Archive Utility 양쪽에서 깨지지
        // 않는다(Java 7+의 EFS Language Encoding Flag가 Charset이 UTF-8일 때 자동으로 켜진다).
        ZipOutputStream(outputStream, StandardCharsets.UTF_8).use { zip ->
            for (entry in contentEntries) {
                val entryName = uniqueEntryName(entry.displayName, usedEntryNames)
                zip.putNextEntry(ZipEntry(entryName))
                zip.write(entry.content)
                zip.closeEntry()
            }
            for (entry in visibleEntries) {
                val file = filesById.getValue(entry.fileId)
                val entryName = uniqueEntryName(entry.displayName, usedEntryNames)
                if (writeEntry(zip, entryName, file)) {
                    includedFileIds += entry.fileId
                } else {
                    failedFileIds += entry.fileId
                }
            }
        }

        if (includedFileIds.isEmpty() && contentEntries.isEmpty()) throw FileArchiveEmptyException()
        return FileArchiveResult(includedFileIds = includedFileIds, skippedFileIds = notVisibleFileIds + failedFileIds)
    }

    private fun validateEntryCount(count: Int) {
        if (count > fileArchiveProperties.maxFileCount) {
            throw FileArchiveTooLargeException("요청 ${count}개, 최대 ${fileArchiveProperties.maxFileCount}개")
        }
    }

    private fun validateTotalSize(totalSizeBytes: Long) {
        if (totalSizeBytes > fileArchiveProperties.maxTotalSizeBytes) {
            throw FileArchiveTooLargeException(
                "총 용량 ${totalSizeBytes}바이트, 최대 ${fileArchiveProperties.maxTotalSizeBytes}바이트",
            )
        }
    }

    /**
     * File 하나를 Storage에서 읽어 ZIP Entry로 쓴다.
     *
     * `fileStoragePort.download`가 Entry를 열기 **전에** 실패하면 그 파일만 건너뛰고 나머지는
     * 계속 담는다(한 사용자의 File 오류가 전체 다운로드를 막지 않는다, Issue #138 요구사항).
     * `putNextEntry` 이후 Body를 쓰는 도중 실패하면(네트워크 중단 등) 이미 일부 쓰인 Entry를
     * 되돌릴 방법이 없어 예외를 그대로 전파한다 -- 이 경로는 흔치 않고, 완전한 원자성을 위해
     * 파일마다 임시 버퍼링을 두는 것은 이번 범위에서 과한 설계로 판단했다(문서화된 한계).
     *
     * @return 성공하면 true, [FileStorageException]으로 건너뛰었으면 false.
     */
    private fun writeEntry(
        zip: ZipOutputStream,
        entryName: String,
        file: StoredFile,
    ): Boolean {
        val input =
            try {
                fileStoragePort.download(file.objectKey)
            } catch (ex: FileStorageException) {
                log.warn("일괄 다운로드 중 File 하나를 건너뛰었습니다. fileId={}", file.id, ex)
                return false
            }
        input.use {
            zip.putNextEntry(ZipEntry(entryName))
            it.copyTo(zip)
            zip.closeEntry()
        }
        return true
    }

    /**
     * ZIP 안에서 이름이 겹치지 않게 하고 경로 구분자를 걷어낸다(ZIP Slip 방지, §23).
     *
     * [FileArchiveEntry.displayName]은 대체로 `StoredFile.originalName`(업로드 시점에
     * `FileNameSanitizer`가 이미 경로 구분자를 제거한 값) 기반이라 대부분 안전하지만, 호출자가
     * 직접 조립한 이름(예: "홍길동/이력서.pdf")까지 신뢰하지 않고 여기서 한 번 더 방어한다.
     */
    private fun uniqueEntryName(
        rawName: String,
        usedEntryNames: MutableSet<String>,
    ): String {
        val replaced =
            rawName
                .replace('/', '_')
                .replace('\\', '_')
                .filterNot(Char::isISOControl)
                .trim()
        // 경로 구분자를 없앤 뒤에도 "."/".."는 그 자체로 위험하다 -- 순진한 압축 해제 코드가
        // File(destDir, entryName)으로 바로 풀면 ".."는 destDir의 **부모** 경로로 해석된다(§23
        // ZIP Slip). 구분자가 아예 없어도 이 두 값만은 별도로 막아야 한다.
        val flattened = if (replaced.isBlank() || replaced == "." || replaced == "..") "file" else replaced
        if (usedEntryNames.add(flattened)) return flattened

        val extension = flattened.substringAfterLast('.', "")
        val base = if (extension.isEmpty()) flattened else flattened.removeSuffix(".$extension")
        var suffix = 2
        while (true) {
            val candidate = if (extension.isEmpty()) "$base($suffix)" else "$base($suffix).$extension"
            if (usedEntryNames.add(candidate)) return candidate
            suffix++
        }
    }
}
