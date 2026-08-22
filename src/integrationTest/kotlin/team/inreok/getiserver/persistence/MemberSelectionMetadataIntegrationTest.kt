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
import team.inreok.getiserver.domain.member.repository.MajorRepository
import team.inreok.getiserver.domain.member.repository.TechStackRepository

/**
 * V5__seed_tech_stacks.sql이 실제로 tech_stacks에, V31__seed_majors.sql이 majors에 각각
 * Seed 데이터를 적재하는지 Testcontainers PostgreSQL로 검증한다(Issue #200).
 */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration::class)
class MemberSelectionMetadataIntegrationTest
    @Autowired
    constructor(
        private val techStackRepository: TechStackRepository,
        private val majorRepository: MajorRepository,
    ) {
        @Test
        fun `tech_stacks에는 V5 Seed Migration이 적재한 데이터가 있다`() {
            val techStacks = techStackRepository.findAll()

            assertThat(techStacks).isNotEmpty
            assertThat(techStacks.map { it.name }).contains("Kotlin", "Spring Boot")
        }

        @Test
        fun `majors에는 V31 Seed Migration이 적재한 데이터가 있다`() {
            val majors = majorRepository.findAll()

            assertThat(majors).isNotEmpty
            assertThat(majors.map { it.name }).contains("백엔드", "프론트엔드", "AI")
            assertThat(majors).allMatch { it.active }
        }

        companion object {
            @Container
            @ServiceConnection
            @JvmStatic
            val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:18.4-alpine"))
        }
    }
