package team.inreok.getiserver.persistence

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
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
import team.inreok.getiserver.domain.member.entity.Member
import team.inreok.getiserver.domain.member.entity.MemberRole
import team.inreok.getiserver.domain.member.entity.MemberRoleId
import team.inreok.getiserver.domain.member.entity.type.AcademicStatus
import team.inreok.getiserver.domain.member.entity.type.MemberStatus
import team.inreok.getiserver.domain.member.entity.type.OAuthProvider
import team.inreok.getiserver.domain.member.entity.type.RoleType
import team.inreok.getiserver.domain.member.repository.MemberRepository
import team.inreok.getiserver.domain.member.repository.MemberRoleRepository

/**
 * `MemberRepository.findIdsByIdInAndStatusAndAcademicStatusAndRole`(Recommendation R4, Issue #160,
 * `RecommendationAudienceQueryPortImpl`이 사용)가 실제 PostgreSQL에서 ACTIVE + ENROLLED + STUDENT
 * 조건과 `member_roles` EXISTS Subquery를 의도대로 적용하는지 검증한다. Service Test(Mock
 * Repository)로는 JPQL 자체의 정확성을 확인할 수 없다.
 */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration::class)
class RecommendationAudienceQueryIntegrationTest
    @Autowired
    constructor(
        private val memberRepository: MemberRepository,
        private val memberRoleRepository: MemberRoleRepository,
    ) {
        @BeforeEach
        fun setUp() {
            memberRoleRepository.deleteAll()
            memberRepository.deleteAll()
        }

        private fun studentOf(
            subject: String,
            status: MemberStatus = MemberStatus.ACTIVE,
            academicStatus: AcademicStatus? = AcademicStatus.ENROLLED,
            role: RoleType = RoleType.STUDENT,
        ): Long {
            val member =
                memberRepository.saveAndFlush(
                    Member(
                        oauthProvider = OAuthProvider.DG,
                        oauthSubject = subject,
                        email = "$subject@example.com",
                        status = status,
                        profilePublic = true,
                    ).apply { this.academicStatus = academicStatus },
                )
            val memberId = requireNotNull(member.id)
            memberRoleRepository.saveAndFlush(MemberRole(MemberRoleId(memberId = memberId, role = role)))
            return memberId
        }

        @Test
        fun `ACTIVE ENROLLED STUDENT 조건을 모두 만족하는 회원만 반환한다`() {
            val eligible = studentOf("eligible")
            val graduated = studentOf("graduated", academicStatus = AcademicStatus.GRADUATED)
            val suspended = studentOf("suspended", status = MemberStatus.SUSPENDED)
            val teacher = studentOf("teacher", role = RoleType.TEACHER)

            val result =
                memberRepository.findIdsByIdInAndStatusAndAcademicStatusAndRole(
                    listOf(eligible, graduated, suspended, teacher),
                    MemberStatus.ACTIVE,
                    AcademicStatus.ENROLLED,
                    RoleType.STUDENT,
                )

            assertThat(result).containsExactly(eligible)
        }

        @Test
        fun `memberIds에 없는 회원은 다른 조건을 만족해도 반환하지 않는다`() {
            studentOf("not-included")

            val result =
                memberRepository.findIdsByIdInAndStatusAndAcademicStatusAndRole(
                    emptyList(),
                    MemberStatus.ACTIVE,
                    AcademicStatus.ENROLLED,
                    RoleType.STUDENT,
                )

            assertThat(result).isEmpty()
        }

        companion object {
            @Container
            @ServiceConnection
            @JvmStatic
            val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:18.4-alpine"))
        }
    }
