package team.inreok.getiserver.domain.file.service.impl

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.file.entity.StoredFile
import team.inreok.getiserver.domain.file.repository.StoredFileRepository
import team.inreok.getiserver.domain.file.service.StoredFileStateWriter

/**
 * `REQUIRES_NEW`를 쓰는 이유는 각 단계가 서로의 성공 여부와 무관하게 확정되어야 하기
 * 때문이다. 특히 업로드 실패를 기록하는 [markFailed]는 호출 측이 예외를 던지는 흐름 안에서
 * 실행되므로, 바깥 Transaction에 참여하면 함께 Rollback되어 실패 사실 자체가 사라진다.
 */
@Component
class StoredFileStateWriterImpl(
    private val storedFileRepository: StoredFileRepository,
) : StoredFileStateWriter {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun createPending(file: StoredFile): StoredFile = storedFileRepository.save(file)

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun markUploaded(fileId: Long) {
        storedFileRepository.findById(fileId).ifPresent { it.markUploaded() }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun markFailed(fileId: Long) {
        storedFileRepository.findById(fileId).ifPresent { it.markFailed() }
    }
}
