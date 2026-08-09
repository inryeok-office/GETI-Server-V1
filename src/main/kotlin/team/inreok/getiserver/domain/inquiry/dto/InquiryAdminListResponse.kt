package team.inreok.getiserver.domain.inquiry.dto

import io.swagger.v3.oas.annotations.media.Schema
import team.inreok.getiserver.domain.inquiry.entity.type.InquiryDiscordDeliveryStatus
import team.inreok.getiserver.domain.inquiry.entity.type.InquiryStatus
import team.inreok.getiserver.domain.inquiry.entity.type.InquiryType
import java.time.LocalDateTime

// global.web.PageResponse는 아직 어떤 Domain도 실제로 쓰지 않고(Program/Member Search가 이미
// Domain 전용 Pagination 응답 DTO를 직접 만드는 관례를 확립했다), 그 관례를 그대로 따랐다
// (InquiryListResponse와 동일한 이유).
@Schema(description = "관리자용 전체 문의 목록 결과. Spring Data Page 규약과 동일하게 page는 0부터 시작한다.")
data class InquiryAdminListResponse(
    @param:Schema(description = "조회 결과 목록")
    val content: List<InquiryAdminListItemResponse>,
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

@Schema(description = "관리자용 문의 목록 항목")
data class InquiryAdminListItemResponse(
    @param:Schema(description = "문의 ID", example = "1")
    val inquiryId: Long,
    @param:Schema(description = "문의 유형", example = "ERROR")
    val inquiryType: InquiryType,
    @param:Schema(description = "문의 제목", example = "로그인이 안 됩니다")
    val title: String,
    @param:Schema(description = "문의 상태", example = "IN_PROGRESS")
    val status: InquiryStatus,
    @param:Schema(description = "작성자 정보")
    val author: InquiryAdminAuthorResponse,
    @param:Schema(description = "담당 개발자 정보. 지정되지 않았으면 null", nullable = true)
    val assignee: InquiryAssigneeResponse?,
    @param:Schema(
        description = "Discord 접수 알림 전달 상태. Notification 연동 전이라 항상 PENDING(Phase 5에서 실제 값 연결 예정)",
        example = "PENDING",
    )
    val discordDeliveryStatus: InquiryDiscordDeliveryStatus,
    @param:Schema(description = "등록 시각")
    val createdAt: LocalDateTime,
    @param:Schema(description = "최초 답변 시각. 아직 답변이 없으면 null", nullable = true)
    val answeredAt: LocalDateTime?,
)

/**
 * 관리자 목록의 작성자 정보다. 원본 요구사항 문서 26절 JSON 예시는 `studentNumber`도 포함하지만,
 * `members` 실제 스키마에 해당 Column이 없어(Program 도메인이 이미 같은 이유로 제외한 선례,
 * `MemberApplicantSnapshotQueryPort` KDoc 참고) 제외했다.
 */
@Schema(description = "관리자 목록의 작성자 정보")
data class InquiryAdminAuthorResponse(
    @param:Schema(description = "작성자 회원 ID", example = "1")
    val memberId: Long,
    @param:Schema(description = "작성자 이름", example = "홍길동", nullable = true)
    val name: String?,
)
