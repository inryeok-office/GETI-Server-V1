package team.inreok.getiserver.domain.application.dto

import io.swagger.v3.oas.annotations.media.Schema
import team.inreok.getiserver.domain.application.entity.type.FormStatus
import java.time.LocalDateTime

@Schema(description = "양식 생성 결과")
data class FormCreateResponse(
    @param:Schema(description = "생성된 양식 ID", example = "1")
    val formId: Long,
    @param:Schema(description = "생성된 Form Version(최초 생성은 항상 1)", example = "1")
    val version: Int,
    @param:Schema(description = "양식 상태", example = "DRAFT")
    val status: FormStatus,
    @param:Schema(description = "생성 일시")
    val createdAt: LocalDateTime,
)
