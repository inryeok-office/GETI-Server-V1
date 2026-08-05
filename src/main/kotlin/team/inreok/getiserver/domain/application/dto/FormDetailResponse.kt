package team.inreok.getiserver.domain.application.dto

import io.swagger.v3.oas.annotations.media.Schema
import team.inreok.getiserver.domain.application.entity.type.FormStatus
import team.inreok.getiserver.domain.application.entity.type.FormType
import java.time.LocalDateTime

@Schema(description = "양식 상세. 항상 현재(최신) Form Version의 필드 구조를 보여준다.")
data class FormDetailResponse(
    @param:Schema(description = "양식 ID", example = "1")
    val formId: Long,
    @param:Schema(description = "양식 이름", example = "2026 하계 인턴 지원서")
    val name: String,
    @param:Schema(description = "양식 대상", example = "JOB")
    val formType: FormType,
    @param:Schema(description = "양식 상태", example = "ACTIVE")
    val status: FormStatus,
    @param:Schema(description = "양식 설명", nullable = true)
    val description: String?,
    @param:Schema(description = "현재 버전의 필드 구조")
    val schemaData: List<FormFieldResponse>,
    @param:Schema(description = "생성 일시")
    val createdAt: LocalDateTime,
    @param:Schema(description = "마지막 수정 일시")
    val updatedAt: LocalDateTime,
)
