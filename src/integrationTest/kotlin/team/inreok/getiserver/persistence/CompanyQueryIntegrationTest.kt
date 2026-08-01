package team.inreok.getiserver.persistence

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.PageRequest
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import team.inreok.getiserver.domain.company.entity.Company
import team.inreok.getiserver.domain.company.entity.type.CompanyType
import team.inreok.getiserver.domain.company.entity.type.MouStatus
import team.inreok.getiserver.domain.company.repository.CompanyRepository
import java.time.LocalDateTime

/**
 * Company 조회 Query가 실제 PostgreSQL에서 의도대로 동작하는지 검증한다.
 * Service/Controller Test는 Mock Repository를 사용하므로 JPQL의 `LIKE ... ESCAPE`,
 * Derived Query의 `IgnoreCase`, V6 Migration의 부분 Unique Index는 이 Test에서만 확인된다.
 */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration::class)
class CompanyQueryIntegrationTest
    @Autowired
    constructor(
        private val companyRepository: CompanyRepository,
    ) {
        private val pageable = PageRequest.of(0, 20)

        @BeforeEach
        fun setUp() {
            companyRepository.deleteAll()
            companyRepository.flush()
        }

        @Test
        fun `검색어로 기업명을 부분 일치 검색한다`() {
            persist("대구인력개발원")
            persist("서울교육원")

            val result = companyRepository.search("인력", null, null, null, pageable)

            assertThat(result.content.map { it.name }).containsExactly("대구인력개발원")
        }

        @Test
        fun `검색어의 대소문자를 무시하고 검색한다`() {
            persist("Geti Company")

            val result = companyRepository.search("geti", null, null, null, pageable)

            assertThat(result.content).hasSize(1)
        }

        @Test
        fun `이스케이프한 퍼센트 기호는 Wildcard가 아니라 리터럴로 매칭된다`() {
            persist("100% 취업 기업")
            persist("100원 취업 기업")

            // Service의 escapeLikePattern이 "100%"를 "100\%"로 바꿔 넘기는 상황을 재현한다.
            val escaped = companyRepository.search("""100\%""", null, null, null, pageable)
            assertThat(escaped.content.map { it.name }).containsExactly("100% 취업 기업")

            // 이스케이프하지 않으면 %가 Wildcard로 해석되어 두 기업이 모두 검색된다.
            val notEscaped = companyRepository.search("100%", null, null, null, pageable)
            assertThat(notEscaped.content).hasSize(2)
        }

        @Test
        fun `이스케이프한 언더스코어도 리터럴로 매칭된다`() {
            persist("A_B 기업")
            persist("AXB 기업")

            val escaped = companyRepository.search("""A\_B""", null, null, null, pageable)
            assertThat(escaped.content.map { it.name }).containsExactly("A_B 기업")

            // 이스케이프하지 않으면 _가 임의의 한 글자를 의미해 두 기업이 모두 검색된다.
            val notEscaped = companyRepository.search("A_B", null, null, null, pageable)
            assertThat(notEscaped.content).hasSize(2)
        }

        @Test
        fun `기업 유형과 MOU 상태로 필터링한다`() {
            persist("공공기관", type = CompanyType.PUBLIC_INSTITUTION, mouStatus = MouStatus.ACTIVE)
            persist("일반기업", type = CompanyType.GENERAL, mouStatus = MouStatus.ACTIVE)
            persist("협약종료기관", type = CompanyType.PUBLIC_INSTITUTION, mouStatus = MouStatus.EXPIRED)

            val result =
                companyRepository.search(null, CompanyType.PUBLIC_INSTITUTION, MouStatus.ACTIVE, null, pageable)

            assertThat(result.content.map { it.name }).containsExactly("공공기관")
        }

        @Test
        fun `삭제된 기업은 검색 결과에서 제외한다`() {
            persist("살아있는기업")
            persist("삭제된기업", deletedAt = LocalDateTime.now())

            val result = companyRepository.search(null, null, null, null, pageable)

            assertThat(result.content.map { it.name }).containsExactly("살아있는기업")
        }

        @Test
        fun `삭제된 기업은 단건 조회에서도 제외한다`() {
            val deleted = persist("삭제된기업", deletedAt = LocalDateTime.now())

            assertThat(companyRepository.findByIdAndDeletedAtIsNull(requireNotNull(deleted.id))).isNull()
        }

        @Test
        fun `이름과 유형이 같은 미삭제 기업을 대소문자 무시하고 찾는다`() {
            persist("Geti", type = CompanyType.GENERAL)

            assertThat(
                companyRepository.existsByNameIgnoreCaseAndTypeAndDeletedAtIsNull("geti", CompanyType.GENERAL),
            ).isTrue()
            // 유형이 다르면 중복이 아니다.
            assertThat(
                companyRepository.existsByNameIgnoreCaseAndTypeAndDeletedAtIsNull("geti", CompanyType.FOREIGN),
            ).isFalse()
        }

        @Test
        fun `삭제된 기업은 중복 판정 대상이 아니다`() {
            persist("삭제된기업", deletedAt = LocalDateTime.now())

            assertThat(
                companyRepository.existsByNameIgnoreCaseAndTypeAndDeletedAtIsNull("삭제된기업", CompanyType.GENERAL),
            ).isFalse()
        }

        @Test
        fun `수정 시 자기 자신은 중복으로 판정하지 않는다`() {
            val company = persist("인력개발원")
            val other = persist("다른기업")

            assertThat(
                companyRepository.existsByNameIgnoreCaseAndTypeAndDeletedAtIsNullAndIdNot(
                    "인력개발원",
                    CompanyType.GENERAL,
                    requireNotNull(company.id),
                ),
            ).isFalse()
            assertThat(
                companyRepository.existsByNameIgnoreCaseAndTypeAndDeletedAtIsNullAndIdNot(
                    "인력개발원",
                    CompanyType.GENERAL,
                    requireNotNull(other.id),
                ),
            ).isTrue()
        }

        @Test
        fun `이름과 유형이 같은 기업을 동시에 저장하면 Unique Index가 차단한다`() {
            persist("인력개발원", type = CompanyType.GENERAL)

            // 사전 중복 검사를 우회하고 곧바로 저장해 경쟁 조건 상황을 재현한다.
            assertThatThrownBy { persist("인력개발원", type = CompanyType.GENERAL) }
                .isInstanceOf(DataIntegrityViolationException::class.java)
        }

        @Test
        fun `대소문자만 다른 같은 이름도 Unique Index가 차단한다`() {
            persist("Geti", type = CompanyType.GENERAL)

            assertThatThrownBy { persist("GETI", type = CompanyType.GENERAL) }
                .isInstanceOf(DataIntegrityViolationException::class.java)
        }

        @Test
        fun `삭제된 기업과 같은 이름은 다시 등록할 수 있다`() {
            persist("인력개발원", deletedAt = LocalDateTime.now())

            val reRegistered = persist("인력개발원")

            assertThat(reRegistered.id).isNotNull()
        }

        private fun persist(
            name: String,
            type: CompanyType = CompanyType.GENERAL,
            mouStatus: MouStatus = MouStatus.NONE,
            deletedAt: LocalDateTime? = null,
        ): Company {
            val company = Company(name = name, type = type, mouStatus = mouStatus)
            company.deletedAt = deletedAt
            return companyRepository.saveAndFlush(company)
        }

        companion object {
            @Container
            @ServiceConnection
            @JvmStatic
            val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:18.4-alpine"))
        }
    }
