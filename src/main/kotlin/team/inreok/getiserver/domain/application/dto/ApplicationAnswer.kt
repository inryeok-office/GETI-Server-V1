package team.inreok.getiserver.domain.application.dto

import io.swagger.v3.oas.annotations.media.Schema
import tools.jackson.databind.JsonNode

@Schema(description = "지원서 답변 1건. fieldId는 지원서 제출 당시 연결된 양식의 Field key(fieldId)와 매칭된다.")
data class ApplicationAnswer(
    @param:Schema(description = "Form Field key(FormFieldResponse.fieldId와 동일)", example = "motivation")
    val fieldId: String,
    @param:Schema(
        description =
            "답변 값. TEXT/TEXTAREA는 문자열, SINGLE_SELECT/MULTI_SELECT는 문자열(배열) 등 " +
                "Field 유형에 맞는 JSON 값을 그대로 담는다.",
        nullable = true,
    )
    val value: JsonNode? = null,
    @param:Schema(
        description =
            "FILE 유형 답변의 첨부파일 ID 목록. File 도메인 연동 전이라 이번 Phase에서는 " +
                "값을 검증하지 않고 그대로 왕복만 시킨다(Phase 6에서 실제 연동).",
        nullable = true,
    )
    val fileIds: List<Long>? = null,
)
