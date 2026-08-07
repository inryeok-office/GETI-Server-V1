package team.inreok.getiserver.domain.file.repository

import org.springframework.data.jpa.repository.JpaRepository
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
