package team.inreok.getiserver.global.health

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Redis 없이(로컬/CI Unit Test 환경) `/actuator/health` 계열 Endpoint가 인증 없이 정상 동작하는지 검증한다.
 * Readiness는 이 환경에서 실제로 DOWN(503)이 되는데, 이는 "필수 의존성 실패 시 503" 요구사항을
 * Mock 없이 있는 그대로 검증하는 결과다. PostgreSQL·Redis가 모두 정상인 UP 경로는
 * `src/integrationTest`의 [HealthReadinessIntegrationTest]가 Testcontainers로 검증한다.
 *
 * `src/test/resources/application.yaml`이 같은 파일 이름으로 `src/main/resources/application.yaml`을
 * 완전히 대체하므로(GetiServerApplicationTests 참고), management와 app.deployment 공통 설정을
 * 여기서 직접 지정한다(이 저장소의 기존 JWT 속성 지정 방식과 동일).
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = [
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=none",
        "app.jwt.secret=health-endpoint-test-only-jwt-secret-value",
        "app.jwt.access-token-expiration-seconds=1800",
        "app.jwt.refresh-token-expiration-seconds=1209600",
        "app.deployment.service=GETI-Server",
        "app.deployment.version=1.2.3",
        "app.deployment.git-sha=a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2",
        "app.deployment.build-time=2026-08-03T12:00:00Z",
        "app.deployment.environment=develop",
        "management.endpoints.web.exposure.include=health,info",
        "management.endpoint.health.show-details=never",
        "management.endpoint.health.probes.enabled=true",
        "management.endpoint.health.group.liveness.include=livenessState",
        "management.endpoint.health.group.readiness.include=readinessState,db,redis",
    ],
)
@AutoConfigureMockMvc
class HealthEndpointTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `Liveness는 인증 없이 200과 UP을 반환하고 상세 정보를 포함하지 않는다`() {
        mockMvc
            .perform(get("/actuator/health/liveness"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.components").doesNotExist())
    }

    @Test
    fun `Readiness는 필수 의존성 실패 시 503과 DOWN을 반환하고 상세 정보를 노출하지 않는다`() {
        mockMvc
            .perform(get("/actuator/health/readiness"))
            .andExpect(status().isServiceUnavailable)
            .andExpect(jsonPath("$.status").value("DOWN"))
            .andExpect(jsonPath("$.components").doesNotExist())
    }

    @Test
    fun `전체 Health Endpoint는 기존 호환성을 위해 계속 응답한다`() {
        val result = mockMvc.perform(get("/actuator/health")).andReturn()

        assertThat(result.response.status).isIn(200, 503)
        assertThat(result.response.contentAsString).contains("\"status\"")
    }

    @Test
    fun `Info는 인증 없이 200과 배포 메타데이터를 반환하고 Secret을 포함하지 않는다`() {
        val result =
            mockMvc
                .perform(get("/actuator/info"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.deployment.service").value("GETI-Server"))
                .andExpect(jsonPath("$.deployment.version").value("1.2.3"))
                .andExpect(jsonPath("$.deployment.gitSha").value("a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2"))
                .andExpect(jsonPath("$.deployment.buildTime").value("2026-08-03T12:00:00Z"))
                .andExpect(jsonPath("$.deployment.environment").value("develop"))
                .andReturn()

        val body = result.response.contentAsString.lowercase()
        assertThat(body).doesNotContain("jwt")
        assertThat(body).doesNotContain("secret")
        assertThat(body).doesNotContain("health-endpoint-test-only-jwt-secret-value")
    }

    @Test
    fun `노출되지 않은 Actuator Endpoint는 404를 반환한다`() {
        mockMvc.perform(get("/actuator/env")).andExpect(status().isNotFound)
        mockMvc.perform(get("/actuator/beans")).andExpect(status().isNotFound)
        mockMvc.perform(get("/actuator/mappings")).andExpect(status().isNotFound)
        mockMvc.perform(get("/actuator/configprops")).andExpect(status().isNotFound)
    }
}
