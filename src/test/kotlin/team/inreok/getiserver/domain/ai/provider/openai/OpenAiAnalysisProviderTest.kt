package team.inreok.getiserver.domain.ai.provider.openai

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import team.inreok.getiserver.domain.ai.entity.type.AiDifficulty
import team.inreok.getiserver.domain.ai.entity.type.AiFitLevel
import team.inreok.getiserver.domain.ai.provider.AiAnalysisInput
import team.inreok.getiserver.domain.ai.provider.AiAnalysisProviderException

/**
 * 실제 OPENAI_API_KEY를 쓰지 않는다 -- [MockRestServiceServer]로 OpenAI Responses API 계약을
 * 고정한다(`HttpDiscordBotClientTest`와 같은 방식).
 */
class OpenAiAnalysisProviderTest {
    private val baseUrl = "http://openai.test"
    private val requestUrl = "$baseUrl/responses"

    private lateinit var server: MockRestServiceServer
    private lateinit var provider: OpenAiAnalysisProvider

    @BeforeEach
    fun setUp() {
        val builder = RestClient.builder().baseUrl(baseUrl)
        server = MockRestServiceServer.bindTo(builder).build()
        provider =
            OpenAiAnalysisProvider(
                properties = OpenAiProperties(apiKey = "test-key", model = "gpt-4o-mini"),
                restClient = builder.build(),
            )
    }

    @Test
    fun `성공하면 Structured Output을 AiAnalysisOutput으로 변환한다`() {
        server
            .expect(requestTo(requestUrl))
            .andExpect(method(org.springframework.http.HttpMethod.POST))
            .andExpect(header("Authorization", "Bearer test-key"))
            .andExpect(jsonPath("$.model").value("gpt-4o-mini"))
            .andExpect(jsonPath("$.text.format.strict").value(true))
            .andRespond(withSuccess(successBody(), MediaType.APPLICATION_JSON))

        val output = provider.analyze(sampleInput())

        assertThat(output.summary).isEqualTo("백엔드 개발자를 채용하는 공고다.")
        assertThat(output.requiredSkillNames).containsExactly("Spring Boot")
        assertThat(output.preferredSkillNames).containsExactly("Docker")
        assertThat(output.highSchoolGraduateFit).isEqualTo(AiFitLevel.SUITABLE)
        assertThat(output.entryLevelFit).isEqualTo(AiFitLevel.SUITABLE)
        assertThat(output.difficulty).isEqualTo(AiDifficulty.NORMAL)
        assertThat(output.model).isEqualTo("gpt-4o-mini")
        server.verify()
    }

    @Test
    fun `API Key가 없으면 호출하지 않고 NotConfigured를 던진다`() {
        val notConfigured = OpenAiAnalysisProvider(OpenAiProperties(apiKey = ""), RestClient.builder().build())

        assertThatThrownBy { notConfigured.analyze(sampleInput()) }
            .isInstanceOf(AiAnalysisProviderException.NotConfigured::class.java)
    }

    @Test
    fun `401은 AuthenticationFailed로 변환한다`() {
        server.expect(requestTo(requestUrl)).andRespond(withStatus(HttpStatus.UNAUTHORIZED))

        assertThatThrownBy { provider.analyze(sampleInput()) }
            .isInstanceOf(AiAnalysisProviderException.AuthenticationFailed::class.java)
    }

    @Test
    fun `429는 RateLimited로 변환한다`() {
        server.expect(requestTo(requestUrl)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS))

        assertThatThrownBy { provider.analyze(sampleInput()) }
            .isInstanceOf(AiAnalysisProviderException.RateLimited::class.java)
    }

    @Test
    fun `500은 ServerError로 변환한다`() {
        server.expect(requestTo(requestUrl)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR))

        assertThatThrownBy { provider.analyze(sampleInput()) }
            .isInstanceOf(AiAnalysisProviderException.ServerError::class.java)
    }

    @Test
    fun `해석할 수 없는 응답은 ResponseInvalid로 변환한다`() {
        server.expect(requestTo(requestUrl)).andRespond(withSuccess("not-json", MediaType.APPLICATION_JSON))

        assertThatThrownBy { provider.analyze(sampleInput()) }
            .isInstanceOf(AiAnalysisProviderException.ResponseInvalid::class.java)
    }

    @Test
    fun `Model이 거부하면 ResponseInvalid로 변환한다`() {
        val refusalBody =
            """
            {"output":[{"type":"message","content":[{"type":"refusal","refusal":"cannot help"}]}]}
            """.trimIndent()
        server.expect(requestTo(requestUrl)).andRespond(withSuccess(refusalBody, MediaType.APPLICATION_JSON))

        assertThatThrownBy { provider.analyze(sampleInput()) }
            .isInstanceOf(AiAnalysisProviderException.ResponseInvalid::class.java)
    }

    @Test
    fun `오류 메시지에 API Key를 담지 않는다`() {
        server.expect(requestTo(requestUrl)).andRespond(withStatus(HttpStatus.UNAUTHORIZED))

        val ex =
            org.assertj.core.api.Assertions
                .catchThrowable { provider.analyze(sampleInput()) }

        assertThat(ex?.message).doesNotContain("test-key")
    }

    private fun sampleInput() =
        AiAnalysisInput(
            jobId = 1L,
            title = "2026 상반기 백엔드 채용",
            postingType = "MOU",
            applicationMethod = "EXTERNAL",
            companyName = "인력개발원",
            content = "## 모집 부문\n- 백엔드 개발자(Kotlin/Spring Boot)",
            startDate = null,
            endDate = null,
            targetGrade = 3,
        )

    private fun successBody(): String =
        """
        {
          "output": [
            {
              "type": "message",
              "content": [
                {
                  "type": "output_text",
                  "text": "{\"summary\":\"백엔드 개발자를 채용하는 공고다.\",\"requiredSkills\":[{\"name\":\"Spring Boot\"}],\"preferredSkills\":[{\"name\":\"Docker\"}],\"highSchoolGraduateFit\":\"SUITABLE\",\"entryLevelFit\":\"SUITABLE\",\"difficulty\":\"NORMAL\"}"
                }
              ]
            }
          ]
        }
        """.trimIndent()
}
