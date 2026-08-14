package team.inreok.getiserver.domain.ai.provider.openai

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import team.inreok.getiserver.domain.ai.provider.AiAnalysisInput
import java.time.LocalDateTime

class OpenAiPromptTest {
    @Test
    fun `JSON Schema는 Strict Mode 요구사항을 만족한다`() {
        val schema = buildJsonSchema()

        assertThat(schema["additionalProperties"]).isEqualTo(false)
        @Suppress("UNCHECKED_CAST")
        val properties = schema["properties"] as Map<String, Any>

        @Suppress("UNCHECKED_CAST")
        val required = schema["required"] as List<String>
        assertThat(required).containsExactlyInAnyOrderElementsOf(properties.keys)
    }

    @Test
    fun `User Input은 null 값을 반복하지 않는다`() {
        val input =
            AiAnalysisInput(
                jobId = 1L,
                title = "백엔드 개발자 채용",
                postingType = "MOU",
                applicationMethod = "EXTERNAL",
                companyName = null,
                content = null,
                startDate = null,
                endDate = null,
                targetGrade = null,
            )

        val text = buildUserInput(input)

        assertThat(text).doesNotContain("기업명")
        assertThat(text).doesNotContain("지원 대상 학년")
        assertThat(text).doesNotContain("모집 기간")
        assertThat(text).doesNotContain("공고 본문")
    }

    @Test
    fun `User Input은 실제 있는 값만 담는다`() {
        val input =
            AiAnalysisInput(
                jobId = 1L,
                title = "백엔드 개발자 채용",
                postingType = "MOU",
                applicationMethod = "EXTERNAL",
                companyName = "인력개발원",
                content = "본문 내용",
                startDate = LocalDateTime.of(2026, 8, 1, 0, 0),
                endDate = null,
                targetGrade = 3,
            )

        val text = buildUserInput(input)

        assertThat(text).contains("기업명: 인력개발원")
        assertThat(text).contains("지원 대상 학년: 3학년")
        assertThat(text).contains("모집 기간: 2026-08-01 ~ 상시")
        assertThat(text).contains("본문 내용")
    }
}
