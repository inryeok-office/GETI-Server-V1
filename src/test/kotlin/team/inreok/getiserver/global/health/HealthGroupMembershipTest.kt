package team.inreok.getiserver.global.health

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import tools.jackson.databind.json.JsonMapper

/**
 * Group의 실제 구성원을 확인하는 White-box Test다(Production 설정은 `show-details: never`를
 * 유지하므로 이 Test에서만 `always`로 재정의해 구성원 이름을 들여다본다). Liveness에 PostgreSQL/
 * Redis가 없고, Readiness에 Collector 외부 API·Discord Webhook 같은 비필수 의존성이 없다는
 * 것을 명시적으로 고정한다.
 *
 * `src/test/resources/application.yaml`이 같은 파일 이름으로 `src/main/resources/application.yaml`을
 * 완전히 대체하므로(GetiServerApplicationTests 참고), management.* 공통 설정을 여기서 직접 지정한다.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = [
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=none",
        "app.jwt.secret=health-group-test-only-jwt-secret-value",
        "app.jwt.access-token-expiration-seconds=1800",
        "app.jwt.refresh-token-expiration-seconds=1209600",
        "management.endpoints.web.exposure.include=health,info",
        "management.endpoint.health.show-details=always",
        "management.endpoint.health.probes.enabled=true",
        "management.endpoint.health.group.liveness.include=livenessState",
        "management.endpoint.health.group.readiness.include=readinessState,db,redis",
    ],
)
@AutoConfigureMockMvc
class HealthGroupMembershipTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    private val objectMapper = JsonMapper()

    @Test
    fun `Liveness Group은 livenessState만 포함한다`() {
        val body =
            mockMvc
                .perform(get("/actuator/health/liveness"))
                .andReturn()
                .response.contentAsString
        val components =
            objectMapper
                .readTree(body)
                .get("components")
                .propertyNames()
                .asSequence()
                .toSet()

        assertThat(components).containsExactly("livenessState")
    }

    @Test
    fun `Readiness Group은 readinessState db redis만 포함하고 Collector나 Discord를 포함하지 않는다`() {
        val body =
            mockMvc
                .perform(get("/actuator/health/readiness"))
                .andReturn()
                .response.contentAsString
        val components =
            objectMapper
                .readTree(body)
                .get("components")
                .propertyNames()
                .asSequence()
                .toSet()

        assertThat(components).containsExactlyInAnyOrder("readinessState", "db", "redis")
    }
}
