package team.inreok.getiserver.domain.application.service.impl

import team.inreok.getiserver.domain.application.dto.FormFieldSchema
import team.inreok.getiserver.domain.application.entity.FormVersion
import team.inreok.getiserver.domain.application.entity.type.FormFieldType
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

private val formFixtureMapper = JsonMapper()

private fun formFieldOf(
    key: String,
    type: FormFieldType,
    order: Int,
    required: Boolean = false,
    options: List<String>? = null,
    filePolicy: JsonNode? = null,
) = FormFieldSchema(
    key = key,
    type = type,
    label = key,
    description = null,
    required = required,
    order = order,
    options = options,
    filePolicy = filePolicy,
)

internal fun formVersionWithAllTypesOf(
    formId: Long = 10L,
    version: Int = 2,
) = FormVersion(
    formId = formId,
    version = version,
    schemaData =
        formFixtureMapper.writeValueAsString(
            listOf(
                formFieldOf("shortAnswer", FormFieldType.TEXT, order = 0, required = true),
                formFieldOf("longAnswer", FormFieldType.TEXTAREA, order = 1),
                formFieldOf("oneChoice", FormFieldType.SINGLE_SELECT, order = 2, options = listOf("A", "B")),
                formFieldOf("manyChoices", FormFieldType.MULTI_SELECT, order = 3, options = listOf("X", "Y")),
                formFieldOf(
                    "resume",
                    FormFieldType.FILE,
                    order = 4,
                    required = true,
                    filePolicy =
                        formFixtureMapper.readTree(
                            """
                            {
                              "extensions": ["pdf", "png", "jpg", "jpeg"],
                              "mimeTypes": ["application/pdf", "image/png", "image/jpeg"],
                              "maxSizeBytes": 10485760,
                              "maxCount": 5
                            }
                            """.trimIndent(),
                        ),
                ),
            ),
        ),
)
