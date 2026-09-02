package team.inreok.getiserver.domain.application.service.impl

import team.inreok.getiserver.domain.application.dto.FormFieldResponse
import team.inreok.getiserver.domain.application.dto.FormFieldSchema
import tools.jackson.databind.ObjectMapper

/**
 * FormVersion의 JSON Schema를 기존 Form 응답 DTO로 변환하는 공용 Mapping helper다.
 * Form 상세와 학생 지원서 응답이 동일한 Schema 해석·Field Mapping을 사용하도록 한다.
 */
internal fun readFormFieldSchemas(
    objectMapper: ObjectMapper,
    schemaData: String,
): List<FormFieldSchema> = objectMapper.readValue(schemaData, Array<FormFieldSchema>::class.java).toList()

internal fun readFormFieldResponses(
    objectMapper: ObjectMapper,
    schemaData: String,
): List<FormFieldResponse> = readFormFieldSchemas(objectMapper, schemaData).map(FormFieldResponse::from)
