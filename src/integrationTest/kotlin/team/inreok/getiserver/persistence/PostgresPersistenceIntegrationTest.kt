package team.inreok.getiserver.persistence

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

/**
 * PostgreSQL Testcontainers를 이용한 Persistence 통합 테스트다.
 *
 * - Flyway가 Testcontainers PostgreSQL에서 Test 전용 Migration(V1__create_persistence_probe.sql)을 실행하는지
 * - Hibernate가 그 Schema에 대해 Entity Mapping을 검증(ddl-auto=validate)하고 실제로 저장/조회할 수 있는지
 *
 * 를 함께 검증한다. 실제 GETI Domain Entity나 비즈니스 Migration은 포함하지 않는다.
 */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration::class)
class PostgresPersistenceIntegrationTest
    @Autowired
    constructor(
        private val repository: PersistenceProbeRepository,
    ) {
        @Test
        fun `Flyway로 생성한 Schema에 저장한 Entity를 PostgreSQL에서 다시 조회할 수 있다`() {
            val saved = repository.save(PersistenceProbeEntity(label = "postgres-integration-test"))
            val savedId = checkNotNull(saved.id) { "저장 직후에는 IDENTITY 전략으로 생성된 id가 있어야 한다" }

            val found = repository.findById(savedId)

            assertThat(found).isPresent
            assertThat(found.get().label).isEqualTo("postgres-integration-test")
        }

        companion object {
            @Container
            @ServiceConnection
            @JvmStatic
            val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:18.4-alpine"))
        }
    }
