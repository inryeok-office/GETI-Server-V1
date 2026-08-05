package team.inreok.getiserver.domain.application.dto

import io.swagger.v3.oas.annotations.media.Schema
import team.inreok.getiserver.domain.application.entity.Form
import team.inreok.getiserver.domain.application.entity.type.FormStatus
import team.inreok.getiserver.domain.application.entity.type.FormType
import java.time.LocalDateTime

@Schema(description = "양식 목록 항목")
data class FormSummaryResponse(
    @param:Schema(description = "양식 ID", example = "1")
    val formId: Long,
    @param:Schema(description = "양식 이름", example = "2026 하계 인턴 지원서")
    val name: String,
    @param:Schema(description = "양식 대상", example = "JOB")
    val formType: FormType,
    @param:Schema(description = "양식 상태", example = "ACTIVE")
    val status: FormStatus,
    @param:Schema(description = "현재 Form Version", example = "2")
    val version: Int,
    @param:Schema(description = "마지막 수정 일시")
    val updatedAt: LocalDateTime,
) {
    companion object {
        fun from(form: Form): FormSummaryResponse =
            FormSummaryResponse(
                formId = requireNotNull(form.id) { "저장된 Form은 id를 가져야 합니다." },
                name = form.name,
                formType = form.formType,
                status = form.status,
                version = form.currentVersion,
                updatedAt = requireNotNull(form.updatedAt) { "저장된 Form은 updatedAt을 가져야 합니다." },
            )
    }
}
