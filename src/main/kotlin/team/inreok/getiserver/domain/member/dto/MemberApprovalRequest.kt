package team.inreok.getiserver.domain.member.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Size

/**
 * 교직원 가입 승인·거절 요청이다(Issue #99). `action`이 REJECT이면 `reason`(거절 사유)이 필수이며,
 * 값이 없으면 `MEMBER_REJECTION_REASON_REQUIRED`(400)로 거부한다. APPROVE는 `reason`을 사용하지
 * 않는다.
 */
@Schema(description = "교직원 가입 승인·거절 요청")
data class MemberApprovalRequest(
    @param:Schema(
        description = "수행할 처리. APPROVE는 승인(ACTIVE + TEACHER 부여), REJECT는 거절(REJECTED).",
        example = "APPROVE",
    )
    val action: ApprovalAction,
    @field:Size(max = 1000, message = "거절 사유는 1000자 이하여야 합니다.")
    @param:Schema(
        description = "거절 사유. action이 REJECT이면 필수이고, APPROVE이면 사용하지 않는다.",
        example = "제출한 재직 증명 자료가 확인되지 않았습니다.",
        nullable = true,
    )
    val reason: String? = null,
)
