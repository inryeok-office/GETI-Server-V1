package team.inreok.getiserver.domain.application.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(description = "양식 수정 결과")
data class FormUpdateResponse(
    @param:Schema(description = "양식 ID", example = "1")
    val formId: Long,
    @param:Schema(description = "수정 후 Form Version", example = "2")
    val version: Int,
    @param:Schema(
        description =
            "이 양식을 사용 중인 공고 ID 목록. 공고-양식 연결은 후속 Phase(Job canApply 연동)에서 " +
                "구현되므로 이번 범위는 항상 빈 배열이다.",
    )
    val affectedJobIds: List<Long>,
    @param:Schema(description = "알림 발송 여부. Notification 미연동이라 항상 false다.", example = "false")
    val notificationCreated: Boolean,
    @param:Schema(description = "수정 일시")
    val updatedAt: LocalDateTime,
)
