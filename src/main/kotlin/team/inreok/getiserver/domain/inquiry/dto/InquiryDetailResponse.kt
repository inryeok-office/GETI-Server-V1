package team.inreok.getiserver.domain.inquiry.dto

import io.swagger.v3.oas.annotations.media.Schema
import team.inreok.getiserver.domain.inquiry.entity.type.InquiryDiscordDeliveryStatus
import team.inreok.getiserver.domain.inquiry.entity.type.InquiryStatus
import team.inreok.getiserver.domain.inquiry.entity.type.InquiryType
import java.time.LocalDateTime

/**
 * 문의·답변 상세 응답이다(요구사항 17절). 소유권 검증(본인 문의만, 개발자는 전체)은
 * `InquiryService.getDetail`이 수행하며, 이 DTO 자체는 그 결과만 담는다.
 */
@Schema(description = "문의·답변 상세 정보")
data class InquiryDetailResponse(
    @param:Schema(description = "문의 ID", example = "1")
    val inquiryId: Long,
    @param:Schema(description = "문의 유형", example = "ERROR")
    val inquiryType: InquiryType,
    @param:Schema(description = "문의 제목", example = "로그인이 안 됩니다")
    val title: String,
    @param:Schema(description = "문의 내용", example = "로그인 버튼을 눌러도 반응이 없습니다.")
    val content: String,
    @param:Schema(description = "문의 상태", example = "RECEIVED")
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
    @param:Schema(
        description =
            "담당 개발자 정보. 학생·교사 응답에서는 항상 null이다 -- 개발자의 운영 정보를 " +
                "일반 사용자에게 노출하지 않기 위함이다(요구사항 17절). 개발자 응답에서는 담당자가 없으면 null.",
        nullable = true,
    )
    val assignee: InquiryAssigneeResponse?,
    @param:Schema(description = "답변 목록. 등록 순(오래된 순)으로 정렬된다")
    val answers: List<InquiryAnswerItemResponse>,
    @param:Schema(description = "등록 시각")
    val createdAt: LocalDateTime,
    @param:Schema(description = "수정 시각")
    val updatedAt: LocalDateTime,
)
