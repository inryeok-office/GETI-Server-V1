package team.inreok.getiserver.domain.file.support

import team.inreok.getiserver.domain.file.entity.StoredFile
import team.inreok.getiserver.domain.file.service.StoredFileStateWriter
import java.time.LocalDateTime

/**
 * DB 없이 상태 전이만 추적하는 Fake다. 실제 구현체는 Transaction 경계만 담당하므로
 * Repository를 Mocking하는 대신 이 자리를 대신한다.
 */
class RecordingStoredFileStateWriter : StoredFileStateWriter {
    val saved = mutableListOf<StoredFile>()

    var failOnMarkUploaded = false

    override fun createPending(file: StoredFile): StoredFile {
        // 실제 저장 시 DB/Hibernate가 채우는 값을 흉내 낸다(IDENTITY의 id, @CreationTimestamp의 createdAt).
        file.id = saved.size + 1L
        file.createdAt = LocalDateTime.now()
        saved += file
        return file
    }

    override fun markUploaded(fileId: Long) {
        check(!failOnMarkUploaded) { "상태 전환 실패를 재현합니다." }
        find(fileId).markUploaded()
    }

    override fun markFailed(fileId: Long) {
        find(fileId).markFailed()
    }

    fun single(): StoredFile = saved.single()

    private fun find(fileId: Long): StoredFile = saved.single { it.id == fileId }
}
