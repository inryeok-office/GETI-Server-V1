package team.inreok.getiserver.domain.program.dto

import io.swagger.v3.oas.annotations.media.Schema
import tools.jackson.databind.JsonNode

// Application 도메인의 ApplicationAnswer(JobApplication 답변)와 동일한 구조·범위 결정을
// 따른다. fieldId·선택지·필수값에 대한 Form Schema 기준 검증은 이번 Phase 범위가 아니다(Job
// Application도 아직 구현하지 않음, FormFieldValidation.kt 주석 참고) — Form Snapshot(formId,
// formVersion)만 기록하고 답변 값은 그대로 왕복시킨다.
@Schema(description = "프로그램 신청 답변 1건. fieldId는 연결된 신청 양식의 Field key와 매칭된다.")
data class ProgramApplicationAnswer(
    @param:Schema(description = "Form Field key", example = "motivation")
    val fieldId: String,
    @param:Schema(description = "답변 값. Field 유형에 맞는 JSON 값을 그대로 담는다.", nullable = true)
    val value: JsonNode? = null,
    @param:Schema(
        description =
            "FILE 유형 답변의 첨부파일 ID 목록. File 도메인 연동 전이라 이번 Phase에서는 " +
                "값을 검증하지 않고 그대로 왕복만 시킨다(Phase 5에서 실제 연동).",
        nullable = true,
    )
    val fileIds: List<Long>? = null,
)
