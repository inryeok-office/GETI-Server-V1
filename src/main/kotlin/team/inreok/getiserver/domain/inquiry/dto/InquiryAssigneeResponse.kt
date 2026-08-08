package team.inreok.getiserver.domain.inquiry.dto

import io.swagger.v3.oas.annotations.media.Schema

/**
 * 담당 개발자 정보다(요구사항 17절/33절). 학생·교사 응답에서는 이 값 자체가 `null`이다
 * (요구사항 17절 "assignee는 DEVELOPER에게만 제공") -- [InquiryDetailResponse.assignee] KDoc 참고.
 */
@Schema(description = "담당 개발자 정보")
data class InquiryAssigneeResponse(
    @param:Schema(description = "담당 개발자 회원 ID", example = "5")
    val memberId: Long,
    @param:Schema(description = "담당 개발자 이름", example = "김개발", nullable = true)
    val name: String?,
)
