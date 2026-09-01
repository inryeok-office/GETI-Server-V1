package team.inreok.getiserver.domain.member.dto

import io.swagger.v3.oas.annotations.media.Schema
import team.inreok.getiserver.domain.member.entity.type.RoleType

/**
 * 회원 Role Set 교체 요청이다(Issue #183 DECISION_REQUIRED 확정 -- 개별 추가/제거 Action이 아니라
 * 요청한 집합으로 전체를 교체하는 PATCH 방식). 비어 있으면 이 회원의 모든 Role을 회수한다.
 */
@Schema(description = "회원 Role Set 교체 요청")
data class AdminMemberRoleUpdateRequest(
    @param:Schema(
        description =
            "이 회원이 가져야 할 최종 Role 집합. 기존에 있던 Role 중 이 집합에 없으면 회수되고, " +
                "없던 Role 중 이 집합에 있으면 새로 부여된다.",
        example = "[\"STUDENT\"]",
    )
    val roles: Set<RoleType>,
)
