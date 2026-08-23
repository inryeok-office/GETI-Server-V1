package team.inreok.getiserver.domain.file.repository

import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
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

    /**
     * [findAllByOwnerTypeAndOwnerIdAndStatus]의 배치 버전이다. 여러 리소스에 연결된 파일을 한
     * 번에 읽어 N+1을 막는다(`FileLinkPort.linkedFilesOf`의 배치 오버로드가 사용).
     */
    fun findAllByOwnerTypeAndOwnerIdInAndStatus(
        ownerType: FileOwnerType,
        ownerIds: Collection<Long>,
        status: FileStatus,
    ): List<StoredFile>

    /** 리소스에 이미 연결된 파일 수. 지시서 §24의 목적별 최대 첨부 개수 검증에 쓴다. */
    fun countByOwnerTypeAndOwnerIdAndPurposeAndStatus(
        ownerType: FileOwnerType,
        ownerId: Long,
        purpose: FilePurpose,
        status: FileStatus,
    ): Long

    /**
     * 관리자 공통 파일 목록 조회다(Issue #225). 화면이 실제로 쓰는 "상태/용도 Filter"와 파일명
     * 검색만 제공한다(과도한 Filter 추가 금지, Issue #225 요구사항). `:status`/`:purpose`는 null이면
     * 조건을 적용하지 않는다(`JobApplicationRepository.search`와 동일한 관례).
     *
     * `:hasOriginalName`이 false면 이름 조건은 항상 참으로 취급된다(`InquiryRepository.searchForAdmin`
     * KDoc의 `:hasQuery`와 동일한 이유 -- `:originalName`을 null로 바인딩하면 Type 추론 문제가
     * 난다). `:originalName`은 Service 계층에서 LIKE Wildcard(%, _)를 미리 이스케이프해 전달한다
     * (`domain.file.service.escapeLikePattern`).
     *
     * `FileStatus`(PENDING/FAILED/DELETED 포함)를 그대로 노출한다 -- 운영 화면은 업로드 중간
     * 상태·고아 파일까지 확인할 수 있어야 하므로, 일반 사용자 조회(`isVisible()`)와 달리 상태로
     * 걸러내지 않는다. 최신 업로드순(`id DESC`)으로 고정 정렬해 `pageable`의 Sort는 무시한다.
     */
    @Query(
        """
        SELECT f FROM StoredFile f
        WHERE (:status IS NULL OR f.status = :status)
          AND (:purpose IS NULL OR f.purpose = :purpose)
          AND (
            :hasOriginalName = FALSE
            OR LOWER(f.originalName) LIKE LOWER(CONCAT('%', :originalName, '%')) ESCAPE '\'
          )
        ORDER BY f.id DESC
        """,
    )
    fun searchForAdmin(
        @Param("status") status: FileStatus?,
        @Param("purpose") purpose: FilePurpose?,
        @Param("hasOriginalName") hasOriginalName: Boolean,
        @Param("originalName") originalName: String,
        pageable: Pageable,
    ): Page<StoredFile>
}
