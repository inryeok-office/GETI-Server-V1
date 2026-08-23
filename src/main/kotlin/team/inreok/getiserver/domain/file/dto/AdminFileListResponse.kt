package team.inreok.getiserver.domain.file.dto

import io.swagger.v3.oas.annotations.media.Schema
import team.inreok.getiserver.domain.file.entity.type.FileOwnerType
import team.inreok.getiserver.domain.file.entity.type.FilePurpose
import team.inreok.getiserver.domain.file.entity.type.FileStatus
import java.time.LocalDateTime

// JobApplicationAdminListResponse/InquiryAdminListResponse/DiscordDeliveryListResponse와 동일한
// 관례를 따른다(global.web.PageResponse는 어떤 Domain도 직접 쓰지 않고, 각 Domain이 전용
// Pagination 응답 DTO를 만드는 관례를 확립했다).
@Schema(description = "관리자 공통 파일 목록 결과. Spring Data Page 규약과 동일하게 page는 0부터 시작한다.")
data class AdminFileListResponse(
    @param:Schema(description = "조회 결과 목록. 최신 업로드순(id DESC)으로 고정 정렬된다.")
    val content: List<AdminFileListItemResponse>,
    @param:Schema(description = "현재 Page 번호(0부터 시작)", example = "0")
    val page: Int,
    @param:Schema(description = "Page당 개수", example = "20")
    val size: Int,
    @param:Schema(description = "전체 결과 개수", example = "3")
    val totalElements: Long,
    @param:Schema(description = "전체 Page 수", example = "1")
    val totalPages: Int,
    @param:Schema(description = "첫 Page 여부", example = "true")
    val first: Boolean,
    @param:Schema(description = "마지막 Page 여부", example = "true")
    val last: Boolean,
)

/**
 * 관리자 공통 파일 목록의 항목 하나다(Issue #225).
 *
 * `usage`(사용처) 표시는 [ownerType]+[ownerId]만으로 최소 계약을 확정했다 -- 대상 표시명(공고
 * 제목 등)까지 채우려면 Job/Program/Application/Portfolio/Member/Company 등 여러 Domain에 새
 * Resolver를 추가해야 하는데, 이 Issue의 범위(관리자 파일 목록 자체)를 벗어난다(Issue #225
 * Architecture 절: "대상 표시명이 필요하면 각 Domain 공개 Resolver를 사용하며 Repository를 직접
 * 참조하지 않는다" -- 아직 그런 Resolver가 없다). 화면은 이 최소 정보로도 "무엇에 연결됐는지"를
 * 종류(ownerType) 단위로는 보여줄 수 있다.
 *
 * 업로더는 [uploader]로 회원 ID와 이름만 노출한다(email/phone 같은 민감 정보는 포함하지 않는다).
 * `domain.member`가 이미 `FileLinkPort`/`FileUrlPort`로 `domain.file`을 참조하므로, 이름은
 * `domain.file.access.FileUploaderAccessor`(File이 소유하고 Member가 구현하는 역방향 SPI)로
 * 배치 조회한다 -- `AdminFileQueryServiceImpl` KDoc 참고. `objectKey`/Bucket/Storage 내부
 * 경로/Credential/Presigned URL은 어떤 Field로도 노출하지 않는다.
 */
@Schema(description = "관리자 공통 파일 목록 항목")
data class AdminFileListItemResponse(
    @param:Schema(description = "파일 ID", example = "42")
    val fileId: Long,
    @param:Schema(description = "원본 파일명", example = "이력서.pdf")
    val originalName: String,
    @param:Schema(description = "파일 크기(Byte)", example = "204800")
    val sizeBytes: Long,
    @param:Schema(description = "Content-Type(서버가 탐지한 값)", example = "application/pdf")
    val contentType: String,
    @param:Schema(description = "업로드 목적", example = "JOB_APPLICATION")
    val purpose: FilePurpose,
    @param:Schema(description = "파일 상태", example = "LINKED")
    val status: FileStatus,
    @param:Schema(description = "업로더 정보. 업로더 회원 ID를 알 수 없으면 null", nullable = true)
    val uploader: AdminFileUploaderResponse?,
    @param:Schema(
        description = "연결된 리소스 종류. 아직 어떤 리소스에도 연결되지 않았으면(UPLOADED 이하) null",
        nullable = true,
        example = "JOB_APPLICATION",
    )
    val ownerType: FileOwnerType?,
    @param:Schema(
        description = "연결된 리소스 ID. 아직 어떤 리소스에도 연결되지 않았으면 null",
        nullable = true,
        example = "15",
    )
    val ownerId: Long?,
    @param:Schema(description = "업로드 일시", example = "2026-08-20T09:00:00")
    val createdAt: LocalDateTime,
)

/**
 * 관리자 목록의 업로더 정보다(`InquiryAdminAuthorResponse`와 동일한 관례). [name]은 회원을 찾을 수
 * 없거나(탈퇴 등) Member가 이름을 아직 입력하지 않았으면 null이다.
 */
@Schema(description = "관리자 목록의 업로더 정보")
data class AdminFileUploaderResponse(
    @param:Schema(description = "업로더 회원 ID", example = "7")
    val memberId: Long,
    @param:Schema(description = "업로더 이름. 알 수 없으면 null", nullable = true, example = "홍길동")
    val name: String?,
)
