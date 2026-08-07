package team.inreok.getiserver.domain.file.service.impl

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import team.inreok.getiserver.domain.file.dto.FileUploadResponse
import team.inreok.getiserver.domain.file.entity.StoredFile
import team.inreok.getiserver.domain.file.entity.type.FilePurpose
import team.inreok.getiserver.domain.file.exception.FileEmptyException
import team.inreok.getiserver.domain.file.exception.FileTooLargeException
import team.inreok.getiserver.domain.file.policy.FileContentTypeValidator
import team.inreok.getiserver.domain.file.policy.FileNameSanitizer
import team.inreok.getiserver.domain.file.policy.FilePolicyProperties
import team.inreok.getiserver.domain.file.service.FileObjectKeyGenerator
import team.inreok.getiserver.domain.file.service.FileUploadService
import team.inreok.getiserver.domain.file.service.StoredFileStateWriter
import team.inreok.getiserver.domain.file.storage.FileStoragePort
import java.io.BufferedInputStream

/**
 * 업로드 흐름이다(요구사항 §6/§10).
 *
 * ```
 * 검증(빈 파일 -> 크기 -> 파일명 -> 확장자 -> 실제 형식)
 *   -> TX1: PENDING Row 커밋 (object_key 선점)
 *   -> Object Storage 업로드            <- Transaction 바깥
 *   -> TX2: UPLOADED로 전환
 * ```
 *
 * Storage 업로드를 먼저 하고 Metadata를 나중에 저장하는 방식(§10의 권장안)을 쓰지 않았다.
 * 그 순서에서는 DB 저장 실패 후 보상 삭제까지 실패하면 **DB에 아무 흔적이 없는 Storage
 * Object**가 남는데, Cleanup은 DB를 기준으로 동작하므로 그 Object는 영원히 발견되지 않는다.
 * PENDING을 먼저 커밋하면 어느 단계에서 죽어도 DB에 흔적이 남는다.
 *
 * 이 Class에는 `@Transactional`을 붙이지 않는다. Storage I/O가 Transaction 안에서 일어나면
 * 안 되기 때문이며, 실제 커밋은 [StoredFileStateWriter]가 별도 Bean으로 수행한다.
 */
@Service
class FileUploadServiceImpl(
    private val storedFileStateWriter: StoredFileStateWriter,
    private val fileStoragePort: FileStoragePort,
    private val filePolicyProperties: FilePolicyProperties,
    private val fileNameSanitizer: FileNameSanitizer,
    private val fileContentTypeValidator: FileContentTypeValidator,
    private val fileObjectKeyGenerator: FileObjectKeyGenerator,
) : FileUploadService {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun upload(
        uploaderMemberId: Long,
        file: MultipartFile,
        purpose: FilePurpose,
    ): FileUploadResponse {
        if (file.isEmpty) throw FileEmptyException()

        val policy = filePolicyProperties.of(purpose)
        if (file.size > policy.maxSizeBytes) {
            throw FileTooLargeException(file.size, policy.maxSizeBytes)
        }

        val sanitizedName = fileNameSanitizer.sanitize(file.originalFilename)

        // Tika가 앞부분만 읽고 되감을 수 있도록 mark를 지원하는 Stream으로 감싼다. 이 Stream을
        // 그대로 Storage에 넘기므로 파일이 두 번 읽히거나 메모리에 적재되지 않는다.
        return BufferedInputStream(file.inputStream).use { inputStream ->
            val detectedContentType =
                fileContentTypeValidator.detectAndValidate(
                    inputStream = inputStream,
                    sanitizedFileName = sanitizedName,
                    declaredContentType = file.contentType,
                    purpose = purpose,
                    policy = policy,
                )

            val pending =
                storedFileStateWriter.createPending(
                    StoredFile(
                        purpose = purpose,
                        objectKey = fileObjectKeyGenerator.generate(purpose),
                        originalName = sanitizedName.displayName,
                        contentType = detectedContentType,
                        sizeBytes = file.size,
                        uploaderMemberId = uploaderMemberId,
                        extension = sanitizedName.extension,
                    ),
                )
            val fileId = requireNotNull(pending.id) { "저장된 파일은 ID를 가져야 합니다." }

            uploadToStorage(pending, fileId, inputStream, detectedContentType)

            storedFileStateWriter.markUploaded(fileId)
            log.info(
                "파일 업로드 완료. fileId={}, purpose={}, size={}, uploaderMemberId={}",
                fileId,
                purpose,
                file.size,
                uploaderMemberId,
            )
            FileUploadResponse.from(pending)
        }
    }

    /**
     * 실패 시 Storage에 남았을 수 있는 Object를 지우고 상태를 FAILED로 남긴다. 보상 삭제 자체가
     * 실패해도 원래 예외를 삼키지 않는다 -- 사용자에게는 업로드 실패가 정확한 결과다.
     */
    private fun uploadToStorage(
        pending: StoredFile,
        fileId: Long,
        inputStream: BufferedInputStream,
        contentType: String,
    ) {
        // 실패 종류를 가리지 않고 보상한다. Storage 예외(FileStorageException)뿐 아니라 Stream
        // 읽기 실패처럼 예상하지 못한 오류에서도 Object가 부분적으로 남았을 수 있기 때문이다.
        // 원래 예외는 getOrThrow로 그대로 올려보내 사용자에게 정확한 실패 이유가 전달되게 한다.
        runCatching {
            fileStoragePort.upload(
                key = pending.objectKey,
                contentType = contentType,
                size = pending.sizeBytes,
                inputStream = inputStream,
            )
        }.onFailure { compensate(fileId, pending.objectKey) }
            .getOrThrow()
    }

    private fun compensate(
        fileId: Long,
        objectKey: String,
    ) {
        runCatching { fileStoragePort.delete(objectKey) }
            .onFailure { log.error("업로드 실패 후 Storage 보상 삭제에 실패했습니다. fileId={}", fileId, it) }
        runCatching { storedFileStateWriter.markFailed(fileId) }
            .onFailure { log.error("업로드 실패 상태 기록에 실패했습니다. fileId={}", fileId, it) }
    }
}
