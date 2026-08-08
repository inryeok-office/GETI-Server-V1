package team.inreok.getiserver.domain.inquiry.dto

import io.swagger.v3.oas.annotations.media.Schema
import team.inreok.getiserver.domain.inquiry.entity.type.InquiryDiscordDeliveryStatus
import team.inreok.getiserver.domain.inquiry.entity.type.InquiryStatus
import team.inreok.getiserver.domain.inquiry.entity.type.InquiryType
import java.time.LocalDateTime

/**
 * 문의 등록 응답이다(요구사항 5절). `status`는 항상 [InquiryStatus.RECEIVED]이고 `answers`는
 * 항상 빈 배열이다 -- 방금 등록한 문의에는 아직 담당자도, 답변도 없다.
 */
@Schema(description = "문의 등록 결과")
data class InquiryCreateResponse(
    @param:Schema(description = "생성된 문의 ID", example = "1")
    val inquiryId: Long,
    @param:Schema(description = "문의 유형", example = "ERROR")
    val inquiryType: InquiryType,
    @param:Schema(description = "문의 제목", example = "로그인이 안 됩니다")
    val title: String,
    @param:Schema(description = "문의 내용", example = "로그인 버튼을 눌러도 반응이 없습니다.")
    val content: String,
    @param:Schema(description = "문의 상태(항상 RECEIVED)", example = "RECEIVED")
    val status: InquiryStatus,
    @param:Schema(description = "작성자 정보")
    val author: InquiryAuthorResponse,
    @param:Schema(description = "첨부파일 목록(연결한 파일이 없으면 빈 배열)")
    val files: List<InquiryFileResponse>,
    @param:Schema(
        description = "Discord 접수 알림 전달 상태. Notification 연동 전이라 항상 PENDING(Phase 5에서 실제 값 연결 예정)",
        example = "PENDING",
    )
    val discordDeliveryStatus: InquiryDiscordDeliveryStatus,
    @param:Schema(description = "답변 목록(항상 빈 배열)")
    val answers: List<InquiryAnswerItemResponse>,
    @param:Schema(description = "등록 시각")
    val createdAt: LocalDateTime,
    @param:Schema(description = "수정 시각")
    val updatedAt: LocalDateTime,
    @param:Schema(
        description = "Discord 전달 완료 여부. discordDeliveryStatus==DELIVERED에서 파생된 값이며 별도로 저장하지 않는다.",
        example = "false",
    )
    val discordDelivered: Boolean,
)
