package team.inreok.getiserver.domain.application.dto

import io.swagger.v3.oas.annotations.media.Schema
import team.inreok.getiserver.domain.application.entity.type.FormFieldType
import tools.jackson.databind.JsonNode

@Schema(description = "양식 필드 상세")
data class FormFieldResponse(
    @param:Schema(description = "필드 식별자(요청의 key와 동일)", example = "motivation")
    val fieldId: String,
    @param:Schema(description = "필드 유형", example = "TEXTAREA")
    val type: FormFieldType,
    @param:Schema(description = "필드 제목(요청의 label과 동일)", example = "지원 동기를 작성해주세요")
    val title: String,
    @param:Schema(description = "필드 설명", nullable = true)
    val description: String?,
    @param:Schema(description = "필수 응답 여부", example = "true")
    val required: Boolean,
    @param:Schema(description = "표시 순서(0부터 시작, fields 배열의 index)", example = "0")
    val order: Int,
    @param:Schema(description = "SINGLE_SELECT/MULTI_SELECT 선택지", nullable = true)
    val options: List<String>?,
    @param:Schema(description = "FILE 유형 첨부파일 정책", nullable = true)
    val filePolicy: JsonNode?,
) {
    companion object {
        fun from(schema: FormFieldSchema): FormFieldResponse =
            FormFieldResponse(
                fieldId = schema.key,
                type = schema.type,
                title = schema.label,
                description = schema.description,
                required = schema.required,
                order = schema.order,
                options = schema.options,
                filePolicy = schema.filePolicy,
            )
    }
}
