package team.inreok.getiserver.domain.application.service.impl

import team.inreok.getiserver.domain.application.dto.FormFieldRequest
import team.inreok.getiserver.domain.application.dto.FormFieldSchema
import team.inreok.getiserver.domain.application.entity.Form
import team.inreok.getiserver.domain.application.entity.type.FormFieldType
import team.inreok.getiserver.domain.application.exception.InvalidFormFieldException

/**
 * 양식 필드 정의 시점 검증이다(요구사항 5.7절). 제출된 답변 값에 대한 검증(필수값 누락, 선택지
 * 범위 확인)은 Application 초안·제출 Phase에서 별도로 구현한다.
 *
 * `order`는 요청으로 받지 않고 `fields` 배열의 index를 그대로 사용한다 — 요구사항 5.2 요청 JSON에
 * `order` Field가 없는데 5.7은 "order 중복 금지"를 요구해, 배열 순서를 그대로 쓰면 중복이 애초에
 * 불가능하다(`docs/application/application-domain-plan.md` §3.6/§4 결정 사항 2 참고).
 */
fun validateAndBuildFieldSchemas(fields: List<FormFieldRequest>): List<FormFieldSchema> {
    val seenKeys = mutableSetOf<String>()

    return fields.mapIndexed { index, field ->
        val key = requireNonBlank(field.key, "필드 key는 공백일 수 없습니다.")
        if (!seenKeys.add(key)) throw InvalidFormFieldException("필드 key '$key'가 중복되었습니다.")
        val label = requireNonBlank(field.label, "필드 label은 공백일 수 없습니다.")

        validateOptions(field)
        validateFilePolicy(field)

        FormFieldSchema(
            key = key,
            type = field.type,
            label = label,
            description = field.description?.trim()?.takeIf { it.isNotEmpty() },
            required = field.required,
            order = index,
            options = field.options,
            filePolicy = field.filePolicy,
        )
    }
}

// FormServiceImpl.update()의 직접 throw 개수를 줄이기 위해 이름 부분 수정만 분리했다(name이
// 전달되지 않으면 아무 일도 하지 않아 기존 값을 유지한다, TooManyFunctions 회피를 위해 최상위
// 함수로 둔다).
fun applyNameIfPresent(
    form: Form,
    name: String?,
) {
    val trimmed = name?.trim() ?: return
    if (trimmed.isEmpty()) throw InvalidFormFieldException("양식 이름은 공백일 수 없습니다.")
    form.name = trimmed
}

private fun requireNonBlank(
    value: String,
    message: String,
): String {
    val trimmed = value.trim()
    if (trimmed.isEmpty()) throw InvalidFormFieldException(message)
    return trimmed
}

private fun validateOptions(field: FormFieldRequest) {
    when (field.type) {
        FormFieldType.SINGLE_SELECT, FormFieldType.MULTI_SELECT -> validateSelectOptions(field)
        FormFieldType.TEXT, FormFieldType.TEXTAREA -> validateNoOptions(field)
        FormFieldType.FILE -> Unit
    }
}

private fun validateSelectOptions(field: FormFieldRequest) {
    val options = field.options
    if (options.isNullOrEmpty()) {
        throw InvalidFormFieldException("필드 '${field.key}'(${field.type})는 선택지가 1개 이상 필요합니다.")
    }
    if (options.size != options.toSet().size) {
        throw InvalidFormFieldException("필드 '${field.key}'의 선택지가 중복되었습니다.")
    }
}

private fun validateNoOptions(field: FormFieldRequest) {
    if (!field.options.isNullOrEmpty()) {
        throw InvalidFormFieldException("필드 '${field.key}'(${field.type})는 선택지를 가질 수 없습니다.")
    }
}

private fun validateFilePolicy(field: FormFieldRequest) {
    val hasFilePolicy = field.filePolicy != null
    if (field.type == FormFieldType.FILE && !hasFilePolicy) {
        throw InvalidFormFieldException("필드 '${field.key}'(FILE)는 filePolicy가 필요합니다.")
    }
    if (field.type != FormFieldType.FILE && hasFilePolicy) {
        throw InvalidFormFieldException("필드 '${field.key}'(${field.type})는 filePolicy를 가질 수 없습니다.")
    }
}
