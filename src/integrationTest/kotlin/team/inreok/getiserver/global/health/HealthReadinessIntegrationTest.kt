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
        // src/main/resources/application.yaml이 이 Classpath에서 우선하는데 app.file.storage는
        // Profile별 파일(application-local/prod.yaml)에만 있어(Secret과 환경별 값이라) 여기서
        // 채운다. 이 Test는 실제 Storage에 접속하지 않고 Bean 생성만 필요하다.
        "app.file.storage.bucket=geti-integration-test",
        "app.file.storage.region=us-east-1",
        "app.file.storage.access-key=integration-test-only-access-key",
        "app.file.storage.secret-key=integration-test-only-secret-key",
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
