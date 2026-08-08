package team.inreok.getiserver.domain.file.service

import team.inreok.getiserver.domain.file.entity.StoredFile

/**
 * 업로드 과정의 상태 전이를 **각각 독립된 Transaction**으로 커밋한다.
 *
 * [FileUploadService] 구현체와 별도 Bean으로 분리한 이유는 Spring의 Proxy 기반
 * `@Transactional`이 같은 Class 안의 Method 호출(self-invocation)에는 적용되지 않기 때문이다.
 * 주입받아 호출해야 각 단계가 실제로 별도 Transaction으로 커밋된다.
 *
 * 느리고 실패할 수 있는 Object Storage I/O를 Transaction 안에서 수행하지 않는다는 원칙(GETI
 * Notion BE 컨벤션 "17. Transaction Convention")을 지키기 위한 구조이기도 하다 -- Storage
 * 호출은 이 Interface의 Method 사이, 즉 Transaction 바깥에서 일어난다.
 */
interface StoredFileStateWriter {
    /**
     * PENDING Row를 먼저 커밋해 `object_key`를 선점한다. 이후 어느 단계에서 실패하더라도 DB에
     * 흔적이 남아 Cleanup(Phase 5)이 고아를 찾을 수 있다(요구사항 §10/§30).
     */
    fun createPending(file: StoredFile): StoredFile

    fun markUploaded(fileId: Long)

    /**
     * Storage 업로드 실패를 기록한다. 보상 삭제까지 마친 뒤 호출한다.
     *
     * 이 호출 자체가 실패해도 업로드 요청은 이미 실패로 응답되며, Row는 PENDING으로 남아
     * Cleanup 대상이 된다.
     */
    fun markFailed(fileId: Long)
}
