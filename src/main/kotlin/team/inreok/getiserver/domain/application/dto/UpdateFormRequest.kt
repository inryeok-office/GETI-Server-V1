package team.inreok.getiserver.domain.application.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import team.inreok.getiserver.domain.application.entity.type.FormStatus

@Schema(
    description =
        "양식 부분 수정 요청. name/description/status는 전달된 값만 반영하고, 전달하지 않으면 " +
            "기존 값을 유지한다. fields를 전달하면 새 Form Version이 생성되고 currentVersion이 " +
            "증가한다(전달하지 않으면 필드 구조와 버전은 그대로 유지된다).",
)
data class UpdateFormRequest(
    @param:Schema(description = "양식 이름(선택)", nullable = true)
    val name: String? = null,
    @param:Schema(description = "양식 설명(선택)", nullable = true)
    val description: String? = null,
    @param:Schema(description = "새 필드 목록(선택). 전달하면 새 Form Version을 만든다.", nullable = true)
    val fields: List<@Valid FormFieldRequest>? = null,
    @param:Schema(description = "양식 상태(선택)", nullable = true)
    val status: FormStatus? = null,
)
