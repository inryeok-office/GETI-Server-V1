package team.inreok.getiserver.domain.inquiry.dto

import io.swagger.v3.oas.annotations.media.Schema
import team.inreok.getiserver.domain.inquiry.entity.type.InquiryStatus

/**
 * 문의 상태 변경 요청이다(요구사항 35절). 허용 전이는 다음과 같고 그 외(CLOSED에서의 모든 전이
 * 포함)는 400 INQUIRY_STATUS_INVALID로 거부한다(사용자 확인 완료, 추가 전이 없음).
 *
 * ```text
 * RECEIVED    -> IN_PROGRESS | ANSWERED
 * IN_PROGRESS -> ANSWERED | CLOSED
 * ANSWERED    -> CLOSED
 * ```
 *
 * `status=ANSWERED`를 직접 지정할 때 답변이 하나도 없으면 같은 Error Code로 거부한다(요구사항
 * 37절, "답변 없이 ANSWERED" 정합성 모순 방지).
 */
@Schema(description = "문의 상태 변경 요청")
data class InquiryStatusUpdateRequest(
    @param:Schema(description = "변경할 상태", example = "IN_PROGRESS")
    val status: InquiryStatus,
)
