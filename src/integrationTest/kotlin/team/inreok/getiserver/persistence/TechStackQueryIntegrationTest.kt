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
import team.inreok.getiserver.domain.member.entity.type.TechStackCategory
import team.inreok.getiserver.domain.member.repository.TechStackRepository

/**
 * TechStack 검색 Query가 실제 PostgreSQL에서 의도대로 동작하는지 검증한다(Issue #257의
 * CompanyRepository.search와 같은 취약 형태, Issue #257 본문이 직접 지목한 후속 대상).
 * V5__seed_tech_stacks.sql이 적재한 Seed 데이터를 그대로 사용한다.
 */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration::class)
class TechStackQueryIntegrationTest
    @Autowired
    constructor(
        private val techStackRepository: TechStackRepository,
    ) {
        @Test
        fun `검색어 없이 반복 조회해도 500 오류가 발생하지 않는다`() {
            // pgjdbc는 동일한 SQL을 같은 Connection에서 prepareThreshold(기본 5회) 이상 실행하면
            // Server-Side Prepared Statement로 전환한다. :query가 null일 때 Parameter 타입을
            // 명확히 알 수 없으면 이 시점에 PostgreSQL이 bytea로 잘못 추론해
            // "function lower(bytea) does not exist"로 Query가 실패한다(CompanyRepository.search와
            // 동일한 취약 형태, Issue #257). 단 한 번의 호출로는 재현되지 않으므로(Threshold 도달
            // 전) 같은 Connection에서 반복 호출해 검증한다.
            repeat(10) {
                val result = techStackRepository.search(null, null)
                assertThat(result.map { it.name }).contains("Kotlin", "Spring Boot")
            }
        }

        @Test
        fun `검색어로 이름을 부분 일치 검색한다`() {
            val result = techStackRepository.search("kotlin", null)

            assertThat(result.map { it.name }).containsExactly("Kotlin")
        }

        @Test
        fun `category로 필터링한다`() {
            val result = techStackRepository.search(null, TechStackCategory.DATABASE)

            assertThat(result.map { it.name })
                .containsExactlyInAnyOrder("PostgreSQL", "MySQL", "MongoDB", "Redis")
        }

        @Test
        fun `검색어와 category를 함께 적용한다`() {
            val result = techStackRepository.search("Post", TechStackCategory.DATABASE)

            assertThat(result.map { it.name }).containsExactly("PostgreSQL")
        }

        companion object {
            @Container
            @ServiceConnection
            @JvmStatic
            val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:18.4-alpine"))
        }
    }
