package team.inreok.getiserver.domain.application.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "교사 지원서 검토 Action 요청")
data class JobApplicationAdminActionRequest(
    @param:Schema(description = "수행할 Action", example = "APPROVE")
    val action: JobApplicationAdminAction,
    @param:Schema(
        description =
            "사유(선택). REQUEST_REVISION/REJECT에서 statusReason으로 기록된다. " +
                "ALLOW_EDIT/APPROVE에서는 전달해도 무시된다.",
        nullable = true,
        example = "자기소개서에 오탈자가 있습니다.",
    )
    val reason: String? = null,
)
