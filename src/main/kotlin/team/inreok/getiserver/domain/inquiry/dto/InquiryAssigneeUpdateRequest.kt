package team.inreok.getiserver.domain.inquiry.dto

import io.swagger.v3.oas.annotations.media.Schema

/**
 * 담당 개발자 지정·해제 요청이다(요구사항 31절). `assigneeId`가 `null`이면 해제,
 * 값이 있으면 그 회원을 담당자로 지정한다 -- 지정 대상은 role=DEVELOPER AND status=ACTIVE를
 * 모두 만족해야 한다(요구사항 32절).
 */
@Schema(description = "담당 개발자 지정·해제 요청")
data class InquiryAssigneeUpdateRequest(
    @param:Schema(
        description = "지정할 담당 개발자 회원 ID. null이면 담당자를 해제한다.",
        example = "5",
        nullable = true,
    )
    val assigneeId: Long?,
)
