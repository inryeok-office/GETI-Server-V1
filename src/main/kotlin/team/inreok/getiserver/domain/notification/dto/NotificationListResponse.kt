package team.inreok.getiserver.domain.notification.dto

import io.swagger.v3.oas.annotations.media.Schema

// ProgramListResponse/FormListResponse와 같은 Domain 전용 Pagination 응답 구조를 그대로 따른다
// (global.web.PageResponse는 아직 어떤 Domain도 쓰지 않는다).
@Schema(description = "내 알림 목록 결과. Spring Data Page 규약과 동일하게 page는 0부터 시작한다.")
data class NotificationListResponse(
    @param:Schema(description = "조회 결과 목록. 최신순(createdAt DESC, 같은 시각이면 id DESC)으로 고정 정렬된다.")
    val content: List<NotificationSummaryResponse>,
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
    @param:Schema(
        description =
            "요청자 본인의 읽지 않은 알림 전체 개수. Header Badge용 값이라 unreadOnly/notificationType " +
                "Filter나 현재 Page와 무관하게 항상 같은 값이다.",
        example = "5",
    )
    val unreadCount: Long,
)
