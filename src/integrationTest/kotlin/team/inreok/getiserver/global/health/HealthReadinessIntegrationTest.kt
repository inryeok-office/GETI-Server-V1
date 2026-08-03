package team.inreok.getiserver.global.health

import com.redis.testcontainers.RedisContainer
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

/**
 * `src/test`(HealthEndpointTest)의 Readiness 검증은 Redis가 없는 환경에서 DOWN 경로만 확인한다.
 * 이 Test는 실제 PostgreSQL과 Redis Testcontainers를 함께 띄워 두 의존성이 모두 정상일 때
 * Readiness가 실제로 200/UP이 되는지 확인한다(Full `@SpringBootTest`라 Flyway Migration도
 * 그대로 실행되며 `ddl-auto=validate`가 실제 Schema를 검증한다).
 */
@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = [
        "app.jwt.secret=readiness-integration-test-only-jwt-secret-value",
        "app.jwt.access-token-expiration-seconds=1800",
        "app.jwt.refresh-token-expiration-seconds=1209600",
    ],
)
@AutoConfigureMockMvc
class HealthReadinessIntegrationTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `PostgreSQL과 Redis가 모두 정상이면 Readiness는 200과 UP을 반환한다`() {
        mockMvc
            .perform(get("/actuator/health/readiness"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("UP"))
    }

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:18.4-alpine"))

        @Container
        @ServiceConnection
        @JvmStatic
        val redis = RedisContainer(DockerImageName.parse("redis:8.8.1-alpine"))
    }
}
