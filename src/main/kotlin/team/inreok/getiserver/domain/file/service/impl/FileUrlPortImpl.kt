package team.inreok.getiserver.domain.file.service.impl

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.file.access.FileAccessResolver
import team.inreok.getiserver.domain.file.entity.type.FilePurpose
import team.inreok.getiserver.domain.file.link.FileUrlPort
import team.inreok.getiserver.domain.file.policy.FileDispositionPolicy
import team.inreok.getiserver.domain.file.repository.StoredFileRepository
import team.inreok.getiserver.domain.file.storage.FileStoragePort
import team.inreok.getiserver.domain.file.storage.FileStorageProperties

@Service
class FileUrlPortImpl(
    private val storedFileRepository: StoredFileRepository,
    private val fileAccessResolver: FileAccessResolver,
    private val fileStoragePort: FileStoragePort,
    private val fileDispositionPolicy: FileDispositionPolicy,
    private val fileStorageProperties: FileStorageProperties,
) : FileUrlPort {
    @Transactional(readOnly = true)
    override fun presignedImageUrls(
        requesterId: Long,
        fileIds: Collection<Long>,
    ): Map<Long, String> {
        if (fileIds.isEmpty()) return emptyMap()

        return storedFileRepository
            .findAllByIdIn(fileIds.toSet())
            // 다운로드 API와 같은 규칙으로 판정한다. 이 검사가 빠지면 fileId만 아는 사용자에게
            // 서명된 URL이 그대로 나가, 같은 Domain이 등록한 FileAccessChecker가 무의미해진다.
            .filter { it.isVisible() && it.purpose in IMAGE_PURPOSES }
            .filter { fileAccessResolver.canAccess(it, requesterId) }
            .mapNotNull { file ->
                // 이미지라도 실제 형식이 inline 허용 목록 밖이면(예: 정책이 바뀌기 전에 올라온
                // 파일) URL을 내주지 않는다. 표시할 수 없는 URL을 주는 것보다 없는 편이 낫다.
                val disposition =
                    fileDispositionPolicy.resolve(
                        contentType = file.contentType,
                        displayName = file.originalName,
                        inlineRequested = true,
                    )
                if (!disposition.inline) return@mapNotNull null

                val url =
                    fileStoragePort.presignedGetUrl(
                        key = file.objectKey,
                        contentDisposition = disposition.headerValue,
                        contentType = disposition.contentType,
                        ttl = fileStorageProperties.presignedUrlTtl,
                    )
                requireNotNull(file.id) to url.toString()
            }.toMap()
    }

    private companion object {
        /** 화면에 바로 표시하는 용도의 목적들. 문서 계열은 다운로드 API를 거친다. */
        private val IMAGE_PURPOSES = setOf(FilePurpose.PROFILE_IMAGE, FilePurpose.COMPANY_LOGO)
    }
}
