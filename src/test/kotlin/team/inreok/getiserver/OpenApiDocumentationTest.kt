package team.inreok.getiserver

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.servlet.mvc.method.RequestMappingInfoHandlerMapping
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

/**
 * OpenAPI 문서(`/v3/api-docs`)의 품질을 검증한다. 이 Test는 단순히 문서가 생성되는지만 보지 않고,
 * 새 Controller/Endpoint를 추가했는데 Swagger Annotation(요약, 설명, Tag, 성공/오류 Response,
 * Parameter 설명, Security 등)을 빠뜨리면 실패한다 — Springdoc은 Annotation이 없어도 Endpoint
 * 자체는 자동으로 문서에 포함시키므로("Path 존재"만으로는 부족), Operation의 세부 품질까지
 * 확인해야 실제 누락을 잡을 수 있다. 규칙 근거는 `docs/ai/openapi-documentation.md` 참고.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = [
        // GetiServerApplicationTests와 동일한 이유로 Flyway/Schema Validation을 비활성화하고
        // JwtProperties Binding에 필요한 최소 값만 채운다(이 Test의 관심사는 OpenAPI 문서 품질이지
        // 실제 DB/JWT 서명 검증이 아니다).
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=none",
        "app.jwt.secret=openapi-test-only-jwt-secret-value",
        "app.jwt.access-token-expiration-seconds=1800",
        "app.jwt.refresh-token-expiration-seconds=1209600",
    ],
)
@AutoConfigureMockMvc
class OpenApiDocumentationTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private lateinit var handlerMapping: RequestMappingInfoHandlerMapping

    private val objectMapper = JsonMapper()

    @Test
    fun `OpenAPI 문서가 정상적으로 생성된다`() {
        val openApi = fetchOpenApiDocument()

        assertThat(openApi.get("openapi").asString()).startsWith("3.")
        assertThat(openApi.get("paths").isEmpty).isFalse()
    }

    @Test
    fun `Swagger UI 페이지가 정상적으로 응답한다`() {
        mockMvc
            .perform(get("/swagger-ui/index.html"))
            .andExpect(status().isOk)
    }

    @Test
    fun `실제 등록된 모든 API Endpoint가 OpenAPI 문서에 존재한다`() {
        val documentedPaths =
            fetchOpenApiDocument()
                .get("paths")
                .propertyNames()
                .asSequence()
                .toSet()
        val actualPaths = actualApiEndpointPaths()

        assertThat(actualPaths).isNotEmpty()
        actualPaths.forEach { path ->
            assertThat(documentedPaths)
                .describedAs(
                    "Endpoint '$path'가 OpenAPI 문서(/v3/api-docs)에 없습니다. Swagger Annotation을 " +
                        "빠뜨렸거나, 의도적으로 숨긴 것이라면 이 Test의 HIDDEN_ENDPOINTS에 사유와 함께 추가하세요.",
                ).contains(path)
        }
    }

    @Test
    fun `모든 Operation은 Tag, summary, description, 최소 하나의 성공 Response를 갖는다`() {
        val paths = fetchOpenApiDocument().get("paths")

        forEachOperation(paths) { path, method, operation ->
            val label = "$method $path"

            assertThat(operation.has("tags") && operation.get("tags").size() > 0)
                .describedAs("$label 에 @Tag가 없습니다")
                .isTrue()
            assertThat(textOrNull(operation, "summary"))
                .describedAs("$label 에 @Operation(summary=...)가 없습니다")
                .isNotBlank()
            assertThat(textOrNull(operation, "description"))
                .describedAs("$label 에 @Operation(description=...)가 없습니다")
                .isNotBlank()

            val responses = operation.get("responses")
            val hasSuccessResponse =
                responses != null &&
                    responses.propertyNames().asSequence().any { code ->
                        code.toIntOrNull()?.let { it in 200..299 } ==
                            true
                    }
            assertThat(hasSuccessResponse)
                .describedAs("$label 에 2xx 성공 @ApiResponse가 없습니다")
                .isTrue()
        }
    }

    @Test
    fun `모든 Path·Query Parameter는 설명을 갖는다`() {
        val paths = fetchOpenApiDocument().get("paths")

        forEachOperation(paths) { path, method, operation ->
            val parameters = operation.get("parameters") ?: return@forEachOperation
            parameters.forEach { parameter ->
                val name = textOrNull(parameter, "name")
                assertThat(textOrNull(parameter, "description"))
                    .describedAs("$method $path 의 Parameter '$name'에 @Parameter(description=...)가 없습니다")
                    .isNotBlank()
            }
        }
    }

    @Test
    fun `인증이 필요한 me, session, logout, members, companies, jobs, admin Endpoint는 Security Requirement를 선언한다`() {
        val paths = fetchOpenApiDocument().get("paths")

        forEachOperation(paths) { path, method, operation ->
            if (AUTH_REQUIRED_PATH_PREFIXES.none { path.startsWith(it) }) return@forEachOperation

            val security = operation.get("security")
            assertThat(security != null && security.size() > 0)
                .describedAs("$method $path 는 인증이 필요한 Endpoint인데 @SecurityRequirement가 없습니다")
                .isTrue()
        }
    }

    private fun fetchOpenApiDocument(): JsonNode {
        val body =
            mockMvc
                .perform(get("/v3/api-docs"))
                .andExpect(status().isOk)
                .andReturn()
                .response.contentAsString
        return objectMapper.readTree(body)
    }

    // Springdoc/Actuator가 자체적으로 등록하는 Endpoint는 API 문서화 대상이 아니므로 제외한다.
    // 의도적으로 문서화하지 않는 실제 API Endpoint가 생기면(현재는 없음) 이유와 함께 여기 추가한다.
    private fun actualApiEndpointPaths(): Set<String> =
        handlerMapping.handlerMethods.keys
            .flatMap { it.directPaths }
            .filterNot { path -> EXCLUDED_PATH_PREFIXES.any { path.startsWith(it) } }
            .filterNot { it in HIDDEN_ENDPOINTS }
            .toSet()

    private fun forEachOperation(
        paths: JsonNode,
        action: (path: String, method: String, operation: JsonNode) -> Unit,
    ) {
        paths.properties().forEach { (path, pathItem) ->
            if (EXCLUDED_PATH_PREFIXES.any { path.startsWith(it) }) return@forEach
            pathItem.properties().forEach { (method, operation) ->
                if (method.uppercase() in HTTP_METHODS) {
                    action(path, method.uppercase(), operation)
                }
            }
        }
    }

    private fun textOrNull(
        node: JsonNode,
        field: String,
    ): String? = node.get(field)?.takeIf { it.isString }?.asString()

    companion object {
        private val HTTP_METHODS = setOf("GET", "POST", "PUT", "PATCH", "DELETE")

        // /error는 Spring Boot의 기본 오류 Fallback Controller(BasicErrorController)이고, /test/web/*는
        // src/test 전용 WebTestSupportController(GlobalExceptionHandlerTest 등이 사용, 실제 서비스에는
        // 포함되지 않음)라 문서화 대상이 아니다.
        private val EXCLUDED_PATH_PREFIXES = listOf("/v3/api-docs", "/swagger-ui", "/actuator", "/error", "/test/")
        private val AUTH_REQUIRED_PATH_PREFIXES =
            listOf(
                "/api/v1/me/",
                "/api/v1/auth/session",
                "/api/v1/auth/logout",
                "/api/v1/members",
                "/api/v1/companies",
                "/api/v1/jobs",
                "/api/v1/job-applications",
                "/api/v1/admin/",
            )

        // 문서화 대상에서 제외할 실제 API Endpoint. 현재는 없다(비워둔 상태를 유지하는 것이 기본값).
        // 새 Endpoint를 의도적으로 Swagger에서 숨겨야 하면 "경로" to "숨기는 이유" 형태로 추가한다.
        private val HIDDEN_ENDPOINTS: Set<String> = emptySet()
    }
}
