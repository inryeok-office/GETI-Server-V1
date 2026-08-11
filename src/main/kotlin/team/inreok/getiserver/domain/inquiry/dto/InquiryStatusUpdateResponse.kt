package team.inreok.getiserver.domain.inquiry.dto

import io.swagger.v3.oas.annotations.media.Schema
import team.inreok.getiserver.domain.inquiry.entity.type.InquiryStatus
import java.time.LocalDateTime

@Schema(description = "문의 상태 변경 결과")
data class InquiryStatusUpdateResponse(
    @param:Schema(description = "문의 ID", example = "1")
    val inquiryId: Long,
    @param:Schema(description = "변경된 문의 상태", example = "IN_PROGRESS")
    val status: InquiryStatus,
    @param:Schema(description = "수정 시각")
    val updatedAt: LocalDateTime,
)
