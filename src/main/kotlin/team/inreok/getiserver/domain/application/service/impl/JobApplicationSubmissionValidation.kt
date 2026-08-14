package team.inreok.getiserver.domain.application.service.impl

import team.inreok.getiserver.domain.application.dto.ApplicationAnswer
import team.inreok.getiserver.domain.application.dto.FormFieldSchema
import team.inreok.getiserver.domain.application.entity.JobApplication
import team.inreok.getiserver.domain.application.exception.ApplicationRequiredAnswerMissingException
import team.inreok.getiserver.domain.application.repository.FormVersionRepository
import tools.jackson.databind.ObjectMapper

/**
 * SUBMIT/RESUBMIT 시점 필수 답변 검증이다(Issue #124 요구사항 SUBMIT 항목). formId/formVersion은
 * createDraft가 AVAILABLE 판정(활성 양식 연결 필수)을 통과해야만 값을 채우므로 항상 존재하지만,
 * 데이터 정합성이 깨진 경우를 대비해 없으면 검증을 건너뛴다(예외로 지원서 자체를 막지 않는다).
 * JobApplicationServiceImpl의 Method 개수를 detekt TooManyFunctions 한도 안에서 유지하기 위해
 * 순수 함수로 분리했다(JobApplicationEligibility.kt의 computeEligibilityReason과 같은 이유).
 */
fun validateRequiredAnswersFilled(
    formVersionRepository: FormVersionRepository,
    objectMapper: ObjectMapper,
    application: JobApplication,
) {
    val requiredSchemas = requiredFieldSchemasOf(formVersionRepository, objectMapper, application)
    if (requiredSchemas.isEmpty()) return

    val answersByKey = readApplicationAnswers(objectMapper, application.answers).associateBy(ApplicationAnswer::fieldId)
    val missing =
        requiredSchemas
            .filter { schema ->
                val answer = answersByKey[schema.key]
                answer == null || (answer.value == null && answer.fileIds.isNullOrEmpty())
            }.map(FormFieldSchema::key)
    if (missing.isNotEmpty()) throw ApplicationRequiredAnswerMissingException(missing)
}

private fun requiredFieldSchemasOf(
    formVersionRepository: FormVersionRepository,
    objectMapper: ObjectMapper,
    application: JobApplication,
): List<FormFieldSchema> {
    val formVersion =
        application.formId?.let { formId ->
            application.formVersion?.let { version -> formVersionRepository.findByFormIdAndVersion(formId, version) }
        } ?: return emptyList()
    return objectMapper
        .readValue(formVersion.schemaData, Array<FormFieldSchema>::class.java)
        .toList()
        .filter(FormFieldSchema::required)
}

private fun readApplicationAnswers(
    objectMapper: ObjectMapper,
    json: String,
): List<ApplicationAnswer> {
    if (json.isBlank()) return emptyList()
    return objectMapper.readValue(json, Array<ApplicationAnswer>::class.java).toList()
}
