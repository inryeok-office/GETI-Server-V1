package team.inreok.getiserver.domain.file.service.impl

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.file.entity.StoredFile
import team.inreok.getiserver.domain.file.entity.type.FileOwnerType
import team.inreok.getiserver.domain.file.entity.type.FilePurpose
import team.inreok.getiserver.domain.file.entity.type.FileStatus
import team.inreok.getiserver.domain.file.exception.FileAlreadyLinkedException
import team.inreok.getiserver.domain.file.exception.FileCountExceededException
import team.inreok.getiserver.domain.file.exception.FileNotFoundException
import team.inreok.getiserver.domain.file.exception.FileNotOwnedException
import team.inreok.getiserver.domain.file.exception.FilePurposeMismatchException
import team.inreok.getiserver.domain.file.link.FileLinkPort
import team.inreok.getiserver.domain.file.link.FileSnapshot
import team.inreok.getiserver.domain.file.policy.FilePolicyProperties
import team.inreok.getiserver.domain.file.repository.StoredFileRepository
import team.inreok.getiserver.global.error.BusinessException
import team.inreok.getiserver.global.error.CommonErrorCode
import java.time.LocalDateTime

@Service
class FileLinkPortImpl(
    private val storedFileRepository: StoredFileRepository,
    private val filePolicyProperties: FilePolicyProperties,
) : FileLinkPort {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun validateAndLink(
        requesterId: Long,
        fileIds: Collection<Long>,
        purpose: FilePurpose,
        ownerId: Long,
    ): List<FileSnapshot> {
        if (fileIds.isEmpty()) return emptyList()

        val distinctIds = fileIds.toSet()
        if (distinctIds.size != fileIds.size) {
            throw BusinessException(CommonErrorCode.INVALID_REQUEST, "중복된 파일 ID가 있습니다.")
        }

        val ownerType = purpose.ownerType
        // Lock을 거는 조회여야 한다. 아래 verifyLinkable의 상태 검사는 in-memory라, Lock 없이
        // 읽으면 같은 파일에 대한 동시 연결 요청이 모두 검사를 통과해 나중 Commit이 먼저 것을
        // 덮어쓴다(StoredFileRepository.findAllByIdInForUpdate 주석 참고).
        val filesById = storedFileRepository.findAllByIdInForUpdate(distinctIds).associateBy { it.id }
        val files = fileIds.map { fileId -> filesById[fileId] ?: throw FileNotFoundException(fileId) }

        files.forEach { verifyLinkable(it, requesterId, purpose) }
        verifyCount(files.size, ownerType, ownerId, purpose)

        val linkedAt = LocalDateTime.now()
        files.forEach { it.linkTo(ownerType, ownerId, linkedAt) }
        log.info(
            "파일을 리소스에 연결했습니다. fileIds={}, purpose={}, ownerType={}, ownerId={}, requesterId={}",
            fileIds,
            purpose,
            ownerType,
            ownerId,
            requesterId,
        )
        return files.map { it.toSnapshot() }
    }

    @Transactional
    override fun unlinkAllOf(
        ownerType: FileOwnerType,
        ownerId: Long,
    ) {
        val linked =
            storedFileRepository.findAllByOwnerTypeAndOwnerIdAndStatus(ownerType, ownerId, FileStatus.LINKED)
        linked.forEach { it.unlink() }
        if (linked.isNotEmpty()) {
            log.info(
                "파일 연결을 해제했습니다. fileIds={}, ownerType={}, ownerId={}",
                linked.map { it.id },
                ownerType,
                ownerId,
            )
        }
    }

    @Transactional(readOnly = true)
    override fun snapshotsOf(fileIds: Collection<Long>): Map<Long, FileSnapshot> {
        if (fileIds.isEmpty()) return emptyMap()
        return storedFileRepository
            .findAllByIdIn(fileIds.toSet())
            .filter { it.isVisible() }
            .associate { requireNotNull(it.id) to it.toSnapshot() }
    }

    @Transactional(readOnly = true)
    override fun linkedFilesOf(
        ownerType: FileOwnerType,
        ownerId: Long,
    ): List<FileSnapshot> =
        storedFileRepository
            .findAllByOwnerTypeAndOwnerIdAndStatus(ownerType, ownerId, FileStatus.LINKED)
            .map { it.toSnapshot() }

    /**
     * 파일 하나가 연결 가능한지 본다. 실패 순서가 곧 정보 노출 정책이다 -- 존재하지 않는 파일과
     * 사용자에게 보이지 않는 상태의 파일을 똑같이 `FILE_NOT_FOUND`로 다뤄, 남의 파일 ID를
     * 넣어보며 존재 여부를 알아내는 것을 막는다.
     */
    private fun verifyLinkable(
        file: StoredFile,
        requesterId: Long,
        purpose: FilePurpose,
    ) {
        val fileId = requireNotNull(file.id)
        val failure: BusinessException? =
            when {
                // PENDING/FAILED/DELETED는 없는 것과 같이 다룬다.
                !file.isVisible() -> FileNotFoundException(fileId)

                // §14의 핵심 방어. File ID는 권한 증명이 아니므로 업로더 본인만 연결할 수 있다.
                file.uploaderMemberId != requesterId -> FileNotOwnedException(fileId)

                file.purpose != purpose -> FilePurposeMismatchException(fileId, purpose)

                // 이미 다른 리소스가 쓰고 있는 파일을 재사용하지 못하게 한다(§14).
                file.status == FileStatus.LINKED -> FileAlreadyLinkedException(fileId)

                else -> null
            }
        if (failure != null) throw failure
    }

    /**
     * 목적별 최대 첨부 개수를 검증한다(요구사항 §24).
     *
     * 업로드 시점이 아니라 연결 시점에 검사하는 이유는, `POST /api/v1/files`가 파일 하나만
     * 다루고 그 시점에는 대상 리소스가 아직 없을 수 있기 때문이다. 파일 10개를 각각 업로드하는
     * 것 자체는 성공하고, 상한이 3인 리소스에 네 번째를 붙일 때 거부된다.
     */
    private fun verifyCount(
        newCount: Int,
        ownerType: FileOwnerType,
        ownerId: Long,
        purpose: FilePurpose,
    ) {
        val maxCount = filePolicyProperties.of(purpose).maxCount
        val alreadyLinked =
            storedFileRepository.countByOwnerTypeAndOwnerIdAndPurposeAndStatus(
                ownerType = ownerType,
                ownerId = ownerId,
                purpose = purpose,
                status = FileStatus.LINKED,
            )
        if (alreadyLinked + newCount > maxCount) throw FileCountExceededException(maxCount)
    }

    private fun StoredFile.toSnapshot(): FileSnapshot =
        FileSnapshot(
            fileId = requireNotNull(id),
            originalName = originalName,
            contentType = contentType,
            size = sizeBytes,
        )
}
