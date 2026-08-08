package team.inreok.getiserver.domain.file.service.impl

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.file.access.FileAccessResolver
import team.inreok.getiserver.domain.file.exception.FileAccessDeniedException
import team.inreok.getiserver.domain.file.exception.FileNotFoundException
import team.inreok.getiserver.domain.file.policy.FileDispositionPolicy
import team.inreok.getiserver.domain.file.repository.StoredFileRepository
import team.inreok.getiserver.domain.file.service.FileDownloadService
import team.inreok.getiserver.domain.file.storage.FileStoragePort
import team.inreok.getiserver.domain.file.storage.FileStorageProperties
import java.net.URI

/**
 * 다운로드 권한 판정과 URL 발급이다(요구사항 §15/§16).
 *
 * 인증만으로는 부족하다. 로그인한 사용자가 fileId만 알면 아무 파일이나 받을 수 있으면 안 되며
 * (§14 IDOR), 파일이 연결된 리소스마다 접근 규칙이 다르다.
 */
@Service
class FileDownloadServiceImpl(
    private val storedFileRepository: StoredFileRepository,
    private val fileAccessResolver: FileAccessResolver,
    private val fileDispositionPolicy: FileDispositionPolicy,
    private val fileStoragePort: FileStoragePort,
    private val fileStorageProperties: FileStorageProperties,
) : FileDownloadService {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional(readOnly = true)
    override fun createDownloadUrl(
        requesterId: Long,
        fileId: Long,
        inlineRequested: Boolean,
    ): URI {
        val file =
            storedFileRepository
                .findById(fileId)
                .orElseThrow { FileNotFoundException(fileId) }

        // PENDING/FAILED/DELETED는 사용자에게 없는 것과 같이 다룬다. 상태를 구분해 알려주면
        // 남의 파일 ID로 존재 여부를 탐색할 수 있게 된다.
        if (!file.isVisible()) throw FileNotFoundException(fileId)

        if (!fileAccessResolver.canAccess(file, requesterId)) {
            log.info("파일 접근이 거부되었습니다. fileId={}, requesterId={}", fileId, requesterId)
            throw FileAccessDeniedException()
        }

        val disposition =
            fileDispositionPolicy.resolve(
                contentType = file.contentType,
                displayName = file.originalName,
                inlineRequested = inlineRequested,
            )
        log.info(
            "다운로드 URL을 발급했습니다. fileId={}, purpose={}, requesterId={}, inline={}",
            fileId,
            file.purpose,
            requesterId,
            disposition.inline,
        )
        // 발급된 URL 자체는 로그에 남기지 않는다(요구사항 §31).
        return fileStoragePort.presignedGetUrl(
            key = file.objectKey,
            contentDisposition = disposition.headerValue,
            contentType = disposition.contentType,
            ttl = fileStorageProperties.presignedUrlTtl,
        )
    }
}
