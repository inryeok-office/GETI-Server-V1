package team.inreok.getiserver.domain.notification.dto

import io.swagger.v3.oas.annotations.media.Schema
import team.inreok.getiserver.domain.notification.entity.type.DiscordDeliveryAction
import team.inreok.getiserver.domain.notification.entity.type.DiscordDeliveryStatus
import team.inreok.getiserver.domain.notification.entity.type.DiscordDeliveryTargetType
import java.time.LocalDateTime

// NotificationListResponse/InquiryAdminListResponse와 같은 Domain 전용 Pagination 응답 구조를
// 그대로 따른다(global.web.PageResponse는 아직 어떤 Domain도 쓰지 않는다).
@Schema(description = "Discord 전달 내역 전체 목록 결과. Spring Data Page 규약과 동일하게 page는 0부터 시작한다.")
data class DiscordDeliveryListResponse(
    @param:Schema(description = "조회 결과 목록. 최신순(id DESC)으로 고정 정렬된다.")
    val content: List<DiscordDeliveryListItemResponse>,
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
 * 관리자 Discord 전달 목록의 항목 하나다(Issue #206).
 *
 * 겹치는 Field는 단일 대상 조회 응답([DiscordDeliveryStatusResponse])과 같은 이름·같은 매핑을
 * 쓰되, 그 DTO를 재사용하지 않고 별도로 둔다 -- 목록에만 필요한 [deliveryId]/[targetName]/
 * [action]을 그쪽에 넣으면 단일 조회 계약이 이유 없이 바뀌고, [targetName]은 단일 조회가 계산하지
 * 않아 항상 null이 되어 오히려 혼란스럽다.
 *
 * **메시지 본문(Client Mock의 `messageBody`)은 담지 않는다.** `discord_deliveries`는 전송 당시
 * Payload를 저장하지 않고(Notification 요구사항 §19·§39), 문의 본문은 §40이 Module 밖 노출을
 * 금지한다. 대상을 식별할 수 있는 최소 정보인 [targetName]만 원본에서 조회 시점에 다시 읽는다.
 */
@Schema(description = "Discord 전달 내역 항목")
data class DiscordDeliveryListItemResponse(
    @param:Schema(description = "Discord 전달 ID", example = "42")
    val deliveryId: Long,
    @param:Schema(description = "대상 종류", example = "JOB")
    val targetType: DiscordDeliveryTargetType,
    @param:Schema(description = "대상 ID", example = "123")
    val targetId: Long,
    @param:Schema(
        description =
            "대상 표시 이름. 공고·프로그램은 제목이고 문의는 유형(InquiryType)이다 -- 문의 제목은 " +
                "개인정보 최소화 원칙에 따라 제공하지 않는다. 원본이 이미 완전히 삭제됐으면 null",
        example = "2026 상반기 신입 백엔드 개발자",
        nullable = true,
    )
    val targetName: String?,
    @param:Schema(description = "Discord 메시지에 수행한 작업", example = "CREATE")
    val action: DiscordDeliveryAction,
    @param:Schema(description = "전송에 사용한 Discord 채널 Snowflake", example = "1234567890123456789")
    val channelId: String,
    @param:Schema(
        description = "Discord 메시지 ID. CREATE가 아직 성공하지 못했으면 null",
        example = "1111111111111111111",
        nullable = true,
    )
    val messageId: String?,
    @param:Schema(description = "전달 상태", example = "FAILED")
    val status: DiscordDeliveryStatus,
    @param:Schema(description = "지금까지 수행한 자동 재시도 횟수", example = "3")
    val automaticRetryCount: Int,
    @param:Schema(description = "지금까지 수행한 수동 재시도 횟수", example = "0")
    val manualRetryCount: Int,
    @param:Schema(description = "자동 재시도 최대 허용 횟수", example = "3")
    val maxAutomaticRetryCount: Int,
    @param:Schema(description = "수동 재시도 최대 허용 횟수", example = "3")
    val maxManualRetryCount: Int,
    @param:Schema(
        description =
            "지금 수동 재시도를 요청할 수 있는지 여부. FAILED이고 수동 상한 미만이면서, 이 항목이 해당 " +
                "대상의 가장 최근 전달일 때만 true다 -- 재시도 API는 대상별 최신 전달만 다시 시도하므로 " +
                "false인 항목에 재시도를 요청하면 409로 거절된다.",
        example = "true",
    )
    val canRetry: Boolean,
    @param:Schema(description = "마지막 실패 코드. 성공했거나 아직 시도한 적이 없으면 null", nullable = true, example = "RATE_LIMITED")
    val failureCode: String?,
    @param:Schema(description = "마지막 실패 사유. 성공했거나 아직 시도한 적이 없으면 null", nullable = true)
    val failureReason: String?,
    @param:Schema(description = "Discord 전달이 예약된 시각", example = "2026-08-20T09:00:00")
    val requestedAt: LocalDateTime,
    @param:Schema(description = "마지막으로 전송을 시도한 시각. 아직 시도한 적이 없으면 null", nullable = true)
    val lastSyncedAt: LocalDateTime?,
)
