package team.inreok.getiserver.domain.inquiry.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

/** 담당 개발자 지정·해제 응답이다(요구사항 33절). 해제했으면 `assignee`가 `null`이다. */
@Schema(description = "담당 개발자 지정·해제 결과")
data class InquiryAssigneeUpdateResponse(
    @param:Schema(description = "문의 ID", example = "1")
    val inquiryId: Long,
    @param:Schema(description = "지정된 담당 개발자 정보. 해제했으면 null", nullable = true)
    val assignee: InquiryAssigneeResponse?,
    @param:Schema(description = "수정 시각")
    val updatedAt: LocalDateTime,
)
