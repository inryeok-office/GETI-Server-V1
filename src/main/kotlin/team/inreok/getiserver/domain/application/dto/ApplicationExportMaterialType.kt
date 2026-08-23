package team.inreok.getiserver.domain.application.dto

import io.swagger.v3.oas.annotations.media.Schema

/** 지원자 export ZIP에 포함할 자료 종류다. 현재 구현된 값만 공개한다(Issue #218). */
@Schema(description = "지원자 export ZIP에 포함할 자료 종류")
enum class ApplicationExportMaterialType {
    @Schema(description = "지원서 생성 시 고정된 지원자 프로필 스냅샷")
    PROFILE,

    @Schema(description = "제출 당시 Form Version 질문과 답변 스냅샷(FILE 답변 제외)")
    ANSWERS,

    @Schema(description = "제출 Snapshot이 참조하는 첨부파일")
    ATTACHMENTS,
}
