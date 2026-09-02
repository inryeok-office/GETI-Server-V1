package team.inreok.getiserver.domain.file.service.impl

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.file.access.FileUploaderAccessor
import team.inreok.getiserver.domain.file.access.FileUploaderSnapshot
import team.inreok.getiserver.domain.file.dto.AdminFileListItemResponse
import team.inreok.getiserver.domain.file.dto.AdminFileListResponse
import team.inreok.getiserver.domain.file.dto.AdminFileUploaderResponse
import team.inreok.getiserver.domain.file.entity.StoredFile
import team.inreok.getiserver.domain.file.entity.type.FilePurpose
import team.inreok.getiserver.domain.file.entity.type.FileStatus
import team.inreok.getiserver.domain.file.repository.StoredFileRepository
import team.inreok.getiserver.domain.file.service.AdminFileQueryService
import team.inreok.getiserver.domain.file.service.escapeLikePattern

/**
 * 관리자 공통 파일 목록 조회 구현이다(Issue #225).
 *
 * 업로더 이름은 [FileUploaderAccessor](File이 소유하고 `domain.member`가 구현하는 역방향
 * SPI)로 배치 조회한다 -- `domain.member`가 이미
 * [team.inreok.getiserver.domain.file.link.FileLinkPort]/
 * [team.inreok.getiserver.domain.file.link.FileUrlPort]로 `domain.file`을 참조하므로
 * (`MemberProfileImageServiceImpl` 등), 여기서 반대로 `domain.member`의 Query Port를 직접
 * 불러오면 두 Module이 서로를 참조하는 순환 의존이 생겨 `ModularityTest`가 실패한다(실제로
 * 시도해 확인함). `FileAccessChecker`와 동일하게 의존을 뒤집어 이 문제를 피했다
 * ([FileUploaderAccessor] KDoc 참고). 목록 Row마다 개별 조회하지 않고 이번 Page에 등장하는
 * 모든 업로더 id를 한 번에 모아 배치로 조회한다(N+1 방지,
 * `InquiryServiceImpl.getAdminList`와 동일한 원칙).
 */
@Service
class AdminFileQueryServiceImpl(
    private val storedFileRepository: StoredFileRepository,
    private val fileUploaderAccessor: FileUploaderAccessor,
) : AdminFileQueryService {
    @Transactional(readOnly = true)
    override fun list(
        status: FileStatus?,
        purpose: FilePurpose?,
        originalName: String?,
        pageable: Pageable,
    ): AdminFileListResponse {
        // 검색어를 보내지 않은 경우와 공백만 보낸 경우를 모두 "검색어 없음"으로 취급한다
        // (JobApplicationAdminServiceImpl.list와 동일한 관례).
        val trimmedName = originalName?.trim()?.takeIf { it.isNotEmpty() }
        val escapedName = trimmedName?.let(::escapeLikePattern)

        val page =
            storedFileRepository.searchForAdmin(
                status = status,
                purpose = purpose,
                hasOriginalName = trimmedName != null,
                originalName = escapedName ?: "",
                pageable = pageable,
            )

        val uploaderIds = page.content.mapNotNull { it.uploaderMemberId }.toSet()
        val uploaders =
            if (uploaderIds.isEmpty()) emptyMap() else fileUploaderAccessor.findAllByIds(uploaderIds)
        return page.toListResponse(uploaders)
    }

    private fun Page<StoredFile>.toListResponse(uploaders: Map<Long, FileUploaderSnapshot>) =
        AdminFileListResponse(
            content = content.map { it.toListItem(uploaders) },
            page = number,
            size = size,
            totalElements = totalElements,
            totalPages = totalPages,
            first = isFirst,
            last = isLast,
        )

    private fun StoredFile.toListItem(uploaders: Map<Long, FileUploaderSnapshot>) =
        AdminFileListItemResponse(
            fileId = requireNotNull(id) { "저장된 StoredFile은 id를 가져야 합니다." },
            originalName = originalName,
            sizeBytes = sizeBytes,
            contentType = contentType,
            purpose = purpose,
            status = status,
            uploader =
                uploaderMemberId?.let { memberId ->
                    AdminFileUploaderResponse(memberId = memberId, name = uploaders[memberId]?.name)
                },
            ownerType = ownerType,
            ownerId = ownerId,
            createdAt = requireNotNull(createdAt) { "저장된 StoredFile은 createdAt을 가져야 합니다." },
        )
}
