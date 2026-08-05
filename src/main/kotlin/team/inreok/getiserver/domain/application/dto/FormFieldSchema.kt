package team.inreok.getiserver.domain.application.dto

import team.inreok.getiserver.domain.application.entity.type.FormFieldType
import tools.jackson.databind.JsonNode

/**
 * `FormVersion.schemaData`(JSONB)에 실제로 저장되는 필드 구조다. `order`는 요청 배열의 index를
 * 그대로 사용한다(요구사항 5.2 요청에 `order` Field가 없어 이렇게 설계했다,
 * `docs/application/application-domain-plan.md` §3.6/§4 참고).
 */
data class FormFieldSchema(
    val key: String,
    val type: FormFieldType,
    val label: String,
    val description: String?,
    val required: Boolean,
    val order: Int,
    val options: List<String>?,
    val filePolicy: JsonNode?,
)
