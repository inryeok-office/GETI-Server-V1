package team.inreok.getiserver.domain.notification.dto

import io.swagger.v3.oas.annotations.media.Schema
import team.inreok.getiserver.domain.notification.entity.type.DiscordDeliveryStatus
import team.inreok.getiserver.domain.notification.entity.type.DiscordSendTargetType
import java.time.LocalDateTime

@Schema(description = "Discord 수동 발송 대상 항목")
data class DiscordSendTargetResponse(
    @param:Schema(description = "대상 종류", example = "JOB")
    val targetType: DiscordSendTargetType,
    @param:Schema(description = "원본 Resource ID", example = "123")
    val targetId: Long,
    @param:Schema(description = "Job/Program 제목", example = "백엔드 개발자 채용")
    val targetName: String,
    @param:Schema(
        description = "대상 학년. null은 전체 학년 대상이며 Job의 기존 targetGrade 계약을 유지한다.",
        example = "[2, 3]",
        nullable = true,
    )
    val targetGrades: List<Int>?,
    @param:Schema(
        description = "최신 CREATE Discord Delivery. null이면 해당 대상에 CREATE Delivery가 아직 존재하지 않는다.",
        nullable = true,
    )
    val delivery: DiscordSendTargetDeliveryResponse?,
)

@Schema(description = "최신 CREATE Discord Delivery 상태")
data class DiscordSendTargetDeliveryResponse(
    @param:Schema(description = "Discord 전달 ID", example = "999")
    val deliveryId: Long,
    @param:Schema(description = "전달 상태", example = "FAILED")
    val status: DiscordDeliveryStatus,
    @param:Schema(description = "Delivery 생성 시각", example = "2026-09-17T09:30:00")
    val requestedAt: LocalDateTime,
    @param:Schema(description = "수동 재시도 횟수", example = "1")
    val manualRetryCount: Int,
    @param:Schema(description = "기존 retry API로 지금 재시도할 수 있는지 여부", example = "true")
    val canRetry: Boolean,
)

@Schema(description = "Discord 수동 발송 대상 목록 결과. page는 0부터 시작한다.")
data class DiscordSendTargetListResponse(
    @param:Schema(description = "PUBLISHED Job/Program 대상 목록. createdAt DESC, id DESC, targetType ASC 순이다.")
    val content: List<DiscordSendTargetResponse>,
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
