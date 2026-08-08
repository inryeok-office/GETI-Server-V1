package team.inreok.getiserver.domain.file.repository

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import team.inreok.getiserver.domain.file.entity.StoredFile
import team.inreok.getiserver.domain.file.entity.type.FileOwnerType
import team.inreok.getiserver.domain.file.entity.type.FilePurpose
import team.inreok.getiserver.domain.file.entity.type.FileStatus

interface StoredFileRepository : JpaRepository<StoredFile, Long> {
    /**
     * 여러 fileId를 한 번에 읽는다. 연결(`FileLinkPort.validateAndLink`)과 이미지 URL 변환
     * (`FileUrlPort.presignedImageUrls`)이 모두 여러 건을 함께 다루므로 단건 조회 반복(N+1)을
     * 만들지 않는다.
     */
    fun findAllByIdIn(ids: Collection<Long>): List<StoredFile>

    /**
     * 연결 대상 파일들을 `PESSIMISTIC_WRITE` Lock으로 읽는다. `FileLinkPort.validateAndLink`는
     * 반드시 이 Method로 조회해야 한다.
     *
     * 상태 검사(`StoredFile.linkTo`의 `UPLOADED` 확인)가 in-memory라, Lock 없이 조회하면 같은
     * 파일을 서로 다른 리소스에 붙이려는 두 요청이 모두 검사를 통과한 뒤 각자 Commit해 나중 값이
     * 먼저 값을 조용히 덮어쓴다(PostgreSQL READ COMMITTED). 그러면 "이미 사용된 파일 재사용
     * 방지"(요구사항 §14) 보장이 깨진다. Row Lock으로 두 요청을 순차화하면 뒤 요청은 갱신된
     * `LINKED` 상태를 읽어 `FileAlreadyLinkedException`으로 거부된다.
     *
     * `ORDER BY f.id`는 Lock 획득 순서를 고정하기 위한 것이다. 여러 Row를 잠그는 요청이
     * `[1, 2]`와 `[2, 1]` 순서로 동시에 들어오면 서로를 기다리는 Deadlock이 될 수 있다.
     *
     * `ProgramRepository.findByIdForUpdate`와 같은 이유와 방식이다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT f FROM StoredFile f WHERE f.id IN :ids ORDER BY f.id")
    fun findAllByIdInForUpdate(
        @Param("ids") ids: Collection<Long>,
    ): List<StoredFile>

    /** 특정 리소스에 현재 연결된 파일들이다. 첨부 개수 상한 검사와 연결 해제에 쓴다. */
    fun findAllByOwnerTypeAndOwnerIdAndStatus(
        ownerType: FileOwnerType,
        ownerId: Long,
        status: FileStatus,
    ): List<StoredFile>

    /** 리소스에 이미 연결된 파일 수. 지시서 §24의 목적별 최대 첨부 개수 검증에 쓴다. */
    fun countByOwnerTypeAndOwnerIdAndPurposeAndStatus(
        ownerType: FileOwnerType,
        ownerId: Long,
        purpose: FilePurpose,
        status: FileStatus,
    ): Long
}
