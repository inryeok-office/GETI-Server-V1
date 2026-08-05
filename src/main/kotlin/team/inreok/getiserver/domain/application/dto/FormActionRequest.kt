package team.inreok.getiserver.domain.application.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "양식 Action 요청")
data class FormActionRequest(
    @param:Schema(description = "수행할 Action", example = "DUPLICATE")
    val action: FormAction,
    @param:Schema(description = "DUPLICATE일 때 사용할 새 이름(선택, 없으면 '{원본 이름} 복사본')", nullable = true)
    val newName: String? = null,
)
