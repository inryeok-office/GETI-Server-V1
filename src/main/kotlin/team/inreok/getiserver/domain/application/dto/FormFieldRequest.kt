package team.inreok.getiserver.domain.application.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import team.inreok.getiserver.domain.application.entity.type.FormFieldType
import tools.jackson.databind.JsonNode

@Schema(description = "양식 필드 정의 요청. order는 별도로 받지 않고 fields 배열의 순서를 그대로 사용한다.")
data class FormFieldRequest(
    @param:Schema(description = "필드 식별 Key. 같은 양식 안에서 고유해야 한다.", example = "motivation")
    @field:NotBlank
    val key: String,
    @param:Schema(description = "필드 유형", example = "TEXTAREA")
    val type: FormFieldType,
    @param:Schema(description = "필드 라벨(질문 제목)", example = "지원 동기를 작성해주세요")
    @field:NotBlank
    val label: String,
    @param:Schema(description = "필드 설명(선택)", nullable = true)
    val description: String? = null,
    @param:Schema(description = "필수 응답 여부", example = "true")
    val required: Boolean = false,
    @param:Schema(
        description = "SINGLE_SELECT/MULTI_SELECT 선택지(1개 이상, 중복 불가). 그 외 유형에는 사용할 수 없다.",
        nullable = true,
    )
    val options: List<String>? = null,
    @param:Schema(
        description =
            "FILE 유형 전용 첨부파일 정책(FILE 유형은 필수, 그 외 유형은 사용 불가). 내부 속성이 " +
                "아직 확정되지 않아 원문 JSON을 그대로 저장·반환한다(결정 필요 사항, " +
                "docs/application/application-domain-plan.md §4).",
        nullable = true,
    )
    val filePolicy: JsonNode? = null,
)
