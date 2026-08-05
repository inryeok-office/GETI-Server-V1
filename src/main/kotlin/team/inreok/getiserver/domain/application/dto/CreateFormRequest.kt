package team.inreok.getiserver.domain.application.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import team.inreok.getiserver.domain.application.entity.type.FormStatus
import team.inreok.getiserver.domain.application.entity.type.FormType

@Schema(description = "개인 신청 양식 생성 요청")
data class CreateFormRequest(
    @param:Schema(description = "양식 이름", example = "2026 하계 인턴 지원서")
    @field:NotBlank
    val name: String,
    @param:Schema(description = "양식 대상. 이번 범위는 JOB만 실제로 사용한다.", example = "JOB")
    val formType: FormType,
    @param:Schema(description = "양식 설명(선택)", nullable = true)
    val description: String? = null,
    @param:Schema(description = "양식 필드 목록. 배열 순서가 곧 표시 순서(order)다.")
    @field:NotEmpty
    val fields: List<@Valid FormFieldRequest>,
    @param:Schema(description = "생성 시 상태. ARCHIVED로는 생성할 수 없다.", example = "DRAFT")
    val status: FormStatus = FormStatus.DRAFT,
)
