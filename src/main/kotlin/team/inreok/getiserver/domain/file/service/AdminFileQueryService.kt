package team.inreok.getiserver.domain.file.service

import org.springframework.data.domain.Pageable
import team.inreok.getiserver.domain.file.dto.AdminFileListResponse
import team.inreok.getiserver.domain.file.entity.type.FilePurpose
import team.inreok.getiserver.domain.file.entity.type.FileStatus

/**
 * 관리자 공통 파일 목록 화면(`admin-common-file-management`)이 쓰는 읽기 전용 조회 계약이다
 * (Issue #225). [FileUploadService]/[FileDownloadService]와 분리한 이유는
 * [team.inreok.getiserver.domain.notification.service.DiscordDeliveryAdminQueryService]와 같다 --
 * 이쪽은 상태를 바꾸지 않는 관리자 읽기 모델이다.
 */
interface AdminFileQueryService {
    /**
     * `status`/`purpose`가 null이면 해당 조건을 적용하지 않는다. `originalName`은 공백을 제외한
     * 값이 있을 때만 부분 검색(대소문자 무시)한다. 정렬은 최신 업로드순으로 고정되어 [pageable]의
     * Sort는 무시하고 Page 정보만 사용한다.
     */
    fun list(
        status: FileStatus?,
        purpose: FilePurpose?,
        originalName: String?,
        pageable: Pageable,
    ): AdminFileListResponse
}
