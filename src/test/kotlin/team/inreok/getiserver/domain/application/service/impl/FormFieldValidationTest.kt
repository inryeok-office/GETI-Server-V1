package team.inreok.getiserver.domain.application.service.impl

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import team.inreok.getiserver.domain.application.dto.FormFieldRequest
import team.inreok.getiserver.domain.application.entity.type.FormFieldType
import team.inreok.getiserver.domain.application.exception.InvalidFormFieldException
import tools.jackson.databind.json.JsonMapper

class FormFieldValidationTest {
    private fun textField(
        key: String = "motivation",
        options: List<String>? = null,
    ) = FormFieldRequest(key = key, type = FormFieldType.TEXT, label = "지원 동기", options = options)

    @Test
    fun `배열 순서를 그대로 order로 채택한다`() {
        val fields =
            listOf(
                FormFieldRequest(key = "a", type = FormFieldType.TEXT, label = "A"),
                FormFieldRequest(key = "b", type = FormFieldType.TEXT, label = "B"),
            )

        val schemas = validateAndBuildFieldSchemas(fields)

        assertThat(schemas[0].order).isZero()
        assertThat(schemas[1].order).isEqualTo(1)
    }

    @Test
    fun `key와 label의 앞뒤 공백을 제거한다`() {
        val fields = listOf(FormFieldRequest(key = " motivation ", type = FormFieldType.TEXT, label = " 지원 동기 "))

        val schemas = validateAndBuildFieldSchemas(fields)

        assertThat(schemas[0].key).isEqualTo("motivation")
        assertThat(schemas[0].label).isEqualTo("지원 동기")
    }

    @Test
    fun `key가 공백이면 InvalidFormFieldException을 던진다`() {
        val fields = listOf(FormFieldRequest(key = "   ", type = FormFieldType.TEXT, label = "지원 동기"))

        assertThatThrownBy { validateAndBuildFieldSchemas(fields) }
            .isInstanceOf(InvalidFormFieldException::class.java)
    }

    @Test
    fun `key가 중복되면 InvalidFormFieldException을 던진다`() {
        val fields =
            listOf(
                FormFieldRequest(key = "motivation", type = FormFieldType.TEXT, label = "A"),
                FormFieldRequest(key = "motivation", type = FormFieldType.TEXT, label = "B"),
            )

        assertThatThrownBy { validateAndBuildFieldSchemas(fields) }
            .isInstanceOf(InvalidFormFieldException::class.java)
    }

    @Test
    fun `label이 공백이면 InvalidFormFieldException을 던진다`() {
        val fields = listOf(FormFieldRequest(key = "motivation", type = FormFieldType.TEXT, label = "   "))

        assertThatThrownBy { validateAndBuildFieldSchemas(fields) }
            .isInstanceOf(InvalidFormFieldException::class.java)
    }

    @Test
    fun `SINGLE_SELECT는 선택지가 없으면 InvalidFormFieldException을 던진다`() {
        val fields =
            listOf(
                FormFieldRequest(
                    key = "gender",
                    type = FormFieldType.SINGLE_SELECT,
                    label = "성별",
                    options = emptyList(),
                ),
            )

        assertThatThrownBy { validateAndBuildFieldSchemas(fields) }
            .isInstanceOf(InvalidFormFieldException::class.java)
    }

    @Test
    fun `MULTI_SELECT는 선택지가 중복되면 InvalidFormFieldException을 던진다`() {
        val fields =
            listOf(
                FormFieldRequest(
                    key = "stack",
                    type = FormFieldType.MULTI_SELECT,
                    label = "기술 스택",
                    options = listOf("Kotlin", "Kotlin"),
                ),
            )

        assertThatThrownBy { validateAndBuildFieldSchemas(fields) }
            .isInstanceOf(InvalidFormFieldException::class.java)
    }

    @Test
    fun `TEXT 유형이 options를 가지면 InvalidFormFieldException을 던진다`() {
        val fields = listOf(textField(options = listOf("A")))

        assertThatThrownBy { validateAndBuildFieldSchemas(fields) }
            .isInstanceOf(InvalidFormFieldException::class.java)
    }

    @Test
    fun `FILE 유형인데 filePolicy가 없으면 InvalidFormFieldException을 던진다`() {
        val fields = listOf(FormFieldRequest(key = "resume", type = FormFieldType.FILE, label = "이력서"))

        assertThatThrownBy { validateAndBuildFieldSchemas(fields) }
            .isInstanceOf(InvalidFormFieldException::class.java)
    }

    @Test
    fun `FILE이 아닌데 filePolicy가 있으면 InvalidFormFieldException을 던진다`() {
        val filePolicy = JsonMapper().readTree("""{"maxSizeBytes":1000000}""")
        val fields = listOf(textField().copy(filePolicy = filePolicy))

        assertThatThrownBy { validateAndBuildFieldSchemas(fields) }
            .isInstanceOf(InvalidFormFieldException::class.java)
    }

    @Test
    fun `FILE 유형에 filePolicy가 있으면 원문 그대로 보관한다`() {
        val filePolicy = JsonMapper().readTree("""{"maxSizeBytes":1000000}""")
        val fields =
            listOf(FormFieldRequest(key = "resume", type = FormFieldType.FILE, label = "이력서", filePolicy = filePolicy))

        val schemas = validateAndBuildFieldSchemas(fields)

        assertThat(schemas[0].filePolicy).isEqualTo(filePolicy)
    }

    @Test
    fun `유효한 필드 구성은 예외 없이 통과한다`() {
        val fields =
            listOf(
                textField(),
                FormFieldRequest(
                    key = "stack",
                    type = FormFieldType.MULTI_SELECT,
                    label = "기술 스택",
                    options = listOf("Kotlin", "Java"),
                ),
            )

        val schemas = validateAndBuildFieldSchemas(fields)

        assertThat(schemas).hasSize(2)
    }
}
