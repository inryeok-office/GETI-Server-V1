package team.inreok.getiserver.persistence

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.testcontainers.junit.jupiter.Testcontainers
import team.inreok.getiserver.domain.member.repository.MajorRepository
import team.inreok.getiserver.domain.member.repository.TechStackRepository

/**
 * V5__seed_tech_stacks.sql이 실제로 tech_stacks에 Seed 데이터를 적재하는지 Testcontainers
 * PostgreSQL로 검증한다. majors는 실제 기관 전공명을 알 수 없어 이 PR에서 Seed하지 않았고,
 * 이 Gap이 회귀로 재발하지 않게 현재 상태(비어 있음)를 명시적으로 남긴다.
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
        fun `majors는 아직 Seed 데이터가 없다(알려진 Gap, 후속 Migration 필요)`() {
            assertThat(majorRepository.findAll()).isEmpty()
        }
    }
