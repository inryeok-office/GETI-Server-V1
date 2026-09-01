package team.inreok.getiserver.domain.member.dto

import io.swagger.v3.oas.annotations.media.Schema
import team.inreok.getiserver.domain.member.entity.type.MemberStatus

/**
 * 계정 상태 변경 요청이다(Issue #183 정책 확정 -- 관리자가 수동으로 전이할 수 있는 것은
 * ACTIVE <-> SUSPENDED뿐이다. 그 외 값은 409(MEMBER_STATUS_TRANSITION_NOT_ALLOWED)로 거부한다).
 */
@Schema(description = "계정 상태 변경 요청")
data class AdminMemberStatusUpdateRequest(
    @param:Schema(
        description = "변경할 계정 상태. ACTIVE <-> SUSPENDED만 허용하며, 그 외 값은 409로 거부한다.",
        example = "SUSPENDED",
    )
    val status: MemberStatus,
)
