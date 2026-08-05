package team.inreok.getiserver.domain.application.dto

import io.swagger.v3.oas.annotations.media.Schema
import team.inreok.getiserver.domain.application.entity.type.FormStatus
import team.inreok.getiserver.domain.application.entity.type.FormType
import java.time.LocalDateTime

@Schema(description = "양식 Action 결과. DUPLICATE는 새로 생성된 양식 기준이다.")
data class FormActionResponse(
    @param:Schema(description = "양식 ID(DUPLICATE는 새로 생성된 양식 ID)", example = "2")
    val formId: Long,
    @param:Schema(description = "양식 이름", example = "2026 하계 인턴 지원서 복사본")
    val name: String,
    @param:Schema(description = "양식 대상", example = "JOB")
    val formType: FormType,
    @param:Schema(description = "Action 수행 후 상태", example = "DRAFT")
    val status: FormStatus,
    @param:Schema(description = "Form Version", example = "1")
    val version: Int,
    @param:Schema(description = "수정 일시")
    val updatedAt: LocalDateTime,
)
