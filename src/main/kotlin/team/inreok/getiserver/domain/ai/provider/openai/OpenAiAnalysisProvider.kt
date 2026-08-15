package team.inreok.getiserver.domain.ai.provider.openai

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.body
import team.inreok.getiserver.domain.ai.entity.type.AiDifficulty
import team.inreok.getiserver.domain.ai.entity.type.AiFitLevel
import team.inreok.getiserver.domain.ai.provider.AiAnalysisInput
import team.inreok.getiserver.domain.ai.provider.AiAnalysisOutput
import team.inreok.getiserver.domain.ai.provider.AiAnalysisProvider
import team.inreok.getiserver.domain.ai.provider.AiAnalysisProviderException

/**
 * OpenAI Responses API Adapter다(GETI Notion AI Analysis Phase 1). Structured Outputs(JSON
 * Schema strict)를 사용해 자유 형식 Text를 Regex로 Parsing하지 않는다.
 *
 * `com.fasterxml.jackson`(Classic Jackson 2.x)을 이 Class 전용으로 직접 쓴다 --
 * `JobAlioCollectorProvider`와 같은 이유다. Spring이 관리하는 Application 전역 `ObjectMapper`
 * Bean은 `tools.jackson`(Jackson 3.x) 기반이라 이 Class의 목적(OpenAI 응답 원문 Parsing)에는
 * 이 Provider가 스스로 관리하는 최소 Instance로 충분하고, 전역 Bean 교체 여부와 분리된다.
 *
 * API Key는 Authorization Header에만 담고, 어떤 로그·예외 메시지에도 원문을 남기지 않는다.
 */
@Component
@EnableConfigurationProperties(OpenAiProperties::class)
class OpenAiAnalysisProvider(
    private val properties: OpenAiProperties,
    @Qualifier(OpenAiHttpConfig.OPENAI_REST_CLIENT) private val restClient: RestClient,
) : AiAnalysisProvider {
    override fun isConfigured(): Boolean = properties.isConfigured()

    override fun analyze(input: AiAnalysisInput): AiAnalysisOutput {
        if (!isConfigured()) throw AiAnalysisProviderException.NotConfigured()

        val responseBody = callResponsesApi(input)
        val schema = extractStructuredOutput(responseBody)
        return AiAnalysisOutput(
            summary = schema.stringOrInvalid("summary"),
            requiredSkillNames = schema.skillNames("requiredSkills"),
            preferredSkillNames = schema.skillNames("preferredSkills"),
            highSchoolGraduateFit =
                parseEnum(
                    schema.stringOrInvalid("highSchoolGraduateFit"),
                    AiFitLevel::valueOf,
                    "highSchoolGraduateFit",
                ),
            entryLevelFit = parseEnum(schema.stringOrInvalid("entryLevelFit"), AiFitLevel::valueOf, "entryLevelFit"),
            difficulty = parseEnum(schema.stringOrInvalid("difficulty"), AiDifficulty::valueOf, "difficulty"),
            model = properties.model,
        )
    }

    @Suppress("ThrowsCount")
    private fun callResponsesApi(input: AiAnalysisInput): String {
        val requestBody =
            mapOf(
                "model" to properties.model,
                "input" to
                    listOf(
                        mapOf("role" to "system", "content" to buildSystemInstruction()),
                        mapOf("role" to "user", "content" to buildUserInput(input)),
                    ),
                "text" to
                    mapOf(
                        "format" to
                            mapOf(
                                "type" to "json_schema",
                                "name" to "job_analysis",
                                "strict" to true,
                                "schema" to buildJsonSchema(),
                            ),
                    ),
            )

        return try {
            restClient
                .post()
                .uri("/responses")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${properties.apiKey}")
                .contentType(MediaType.APPLICATION_JSON)
                .body(JSON_MAPPER.writeValueAsString(requestBody))
                .retrieve()
                .onStatus({ status: HttpStatusCode ->
                    status == HttpStatus.UNAUTHORIZED ||
                        status == HttpStatus.FORBIDDEN
                }) { _, _ ->
                    throw AiAnalysisProviderException.AuthenticationFailed()
                }.onStatus({ status: HttpStatusCode -> status == HttpStatus.TOO_MANY_REQUESTS }) { _, _ ->
                    throw AiAnalysisProviderException.RateLimited()
                }.onStatus({ status: HttpStatusCode -> status.is4xxClientError }) { _, response ->
                    throw AiAnalysisProviderException.ClientError(
                        "OpenAI가 잘못된 요청 오류를 반환했습니다(${response.statusCode.value()}).",
                    )
                }.onStatus({ status: HttpStatusCode -> status.is5xxServerError }) { _, response ->
                    throw AiAnalysisProviderException.ServerError(
                        "OpenAI가 서버 오류를 반환했습니다(${response.statusCode.value()}).",
                    )
                }.body<String>()
                ?: throw AiAnalysisProviderException.ResponseInvalid("OpenAI 응답 본문이 비어 있습니다.")
        } catch (ex: AiAnalysisProviderException) {
            throw ex
        } catch (ex: org.springframework.web.client.ResourceAccessException) {
            // RestClient(DefaultRestClient)는 전송 중 발생한 모든 IOException(SocketTimeoutException
            // 포함)을 ResourceAccessException으로 감싸서 던진다 -- 별도의
            // catch (ex: SocketTimeoutException) 분기는 절대 도달하지 않는 Dead Code라 두지
            // 않는다(Code Review 지적 사항, Issue #132). 원인 Exception으로 실제 Timeout 여부를
            // 구분하고, 메시지는 URL 등 원문을 담을 수 있는 ex.message 대신 다른 분기와 같은
            // 고정 문구만 쓴다.
            if (ex.cause is java.net.SocketTimeoutException) {
                throw AiAnalysisProviderException.Timeout(cause = ex)
            }
            throw AiAnalysisProviderException.NetworkError(cause = ex)
        }
    }

    /**
     * Responses API 응답의 `output[].content[]`에서 `type=output_text`인 Text를 찾아 우리
     * Schema로 Parsing한다. Model이 거부(`type=refusal`)하거나 형식이 예상과 다르면
     * [AiAnalysisProviderException.ResponseInvalid]를 던진다.
     *
     * Kotlin Data Class로 직접 Bind하지 않고 Map으로 Parsing한다(JobAlioCollectorProvider와 같은
     * 이유) -- 이 Provider가 쓰는 Classic Jackson(com.fasterxml.jackson) Instance에는
     * jackson-module-kotlin이 등록되어 있지 않아, Kotlin Data Class를 직접 Bind하면
     * InvalidDefinitionException이 발생한다.
     */
    @Suppress("TooGenericExceptionCaught", "ThrowsCount")
    private fun extractStructuredOutput(responseBody: String): Map<*, *> {
        val root =
            try {
                JSON_MAPPER.readValue(responseBody, Map::class.java)
            } catch (ex: Exception) {
                throw AiAnalysisProviderException.ResponseInvalid("OpenAI 응답 JSON을 해석할 수 없습니다.", cause = ex)
            }

        val outputItems = (root["output"] as? List<*>)?.filterIsInstance<Map<*, *>>().orEmpty()
        val message =
            outputItems.firstOrNull { it["type"] == "message" }
                ?: throw AiAnalysisProviderException.ResponseInvalid("OpenAI 응답에 message Output이 없습니다.")
        val contentItems = (message["content"] as? List<*>)?.filterIsInstance<Map<*, *>>().orEmpty()

        if (contentItems.any { it["type"] == "refusal" }) {
            throw AiAnalysisProviderException.ResponseInvalid("OpenAI가 이번 공고 분석 요청을 거부했습니다.")
        }
        val text =
            contentItems.firstOrNull { it["type"] == "output_text" }?.get("text") as? String
                ?: throw AiAnalysisProviderException.ResponseInvalid("OpenAI 응답에 output_text가 없습니다.")

        return try {
            JSON_MAPPER.readValue(text, Map::class.java)
        } catch (ex: Exception) {
            throw AiAnalysisProviderException.ResponseInvalid("OpenAI Structured Output을 해석할 수 없습니다.", cause = ex)
        }
    }

    private fun Map<*, *>.stringOrInvalid(field: String): String =
        this[field] as? String
            ?: throw AiAnalysisProviderException.ResponseInvalid("OpenAI 응답에 $field 필드가 없습니다.")

    private fun Map<*, *>.skillNames(field: String): List<String> =
        (this[field] as? List<*>)
            ?.filterIsInstance<Map<*, *>>()
            ?.mapNotNull { it["name"] as? String }
            ?: throw AiAnalysisProviderException.ResponseInvalid("OpenAI 응답에 $field 필드가 없습니다.")

    private fun <T : Enum<T>> parseEnum(
        raw: String,
        valueOf: (String) -> T,
        fieldName: String,
    ): T =
        try {
            valueOf(raw)
        } catch (ex: IllegalArgumentException) {
            throw AiAnalysisProviderException.ResponseInvalid("OpenAI 응답의 $fieldName 값이 올바르지 않습니다.", cause = ex)
        }

    private companion object {
        val JSON_MAPPER: ObjectMapper = ObjectMapper()
    }
}
