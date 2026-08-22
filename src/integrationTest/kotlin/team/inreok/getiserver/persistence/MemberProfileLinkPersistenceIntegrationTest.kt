package team.inreok.getiserver.persistence

import jakarta.persistence.EntityManager
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
import team.inreok.getiserver.domain.member.entity.Member
import team.inreok.getiserver.domain.member.entity.MemberProfileLink
import team.inreok.getiserver.domain.member.entity.type.OAuthProvider
import team.inreok.getiserver.domain.member.repository.MemberProfileLinkRepository
import team.inreok.getiserver.domain.member.repository.MemberRepository

/**
 * V31__create_member_profile_links.sql이 실제 PostgreSQL에서 순서(display_order) 조회와
 * member 삭제 시 CASCADE 삭제를 의도대로 수행하는지 Testcontainers로 검증한다(Issue #220).
 */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration::class)
class MemberProfileLinkPersistenceIntegrationTest
    @Autowired
    constructor(
        private val entityManager: EntityManager,
        private val memberRepository: MemberRepository,
        private val memberProfileLinkRepository: MemberProfileLinkRepository,
    ) {
        @Test
        fun `표시 순서(display_order)대로 정렬해 조회한다`() {
            val member = persistMember("links-order-subject")
            memberProfileLinkRepository.saveAll(
                listOf(
                    MemberProfileLink(memberId = member.id!!, label = "포트폴리오", url = "https://portfolio.example.com", displayOrder = 1),
                    MemberProfileLink(memberId = member.id!!, label = "블로그", url = "https://blog.example.com", displayOrder = 0),
                ),
            )
            memberProfileLinkRepository.flush()
            entityManager.clear()

            val links = memberProfileLinkRepository.findAllByMemberIdOrderByDisplayOrderAsc(member.id!!)

            assertThat(links.map { it.label }).containsExactly("블로그", "포트폴리오")
        }

        @Test
        fun `member가 삭제되면 member_profile_links도 함께 삭제된다`() {
            val member = persistMember("links-cascade-subject")
            memberProfileLinkRepository.saveAndFlush(
                MemberProfileLink(memberId = member.id!!, label = "블로그", url = "https://blog.example.com", displayOrder = 0),
            )

            memberRepository.delete(member)
            memberRepository.flush()
            // ON DELETE CASCADE는 PostgreSQL이 DB 차원에서 수행하므로 Hibernate 1차 Cache에는
            // 반영되지 않는다. 다시 조회하기 전에 영속성 Context를 비워 실제 DB 상태를 확인한다.
            entityManager.clear()

            assertThat(memberProfileLinkRepository.findAllByMemberIdOrderByDisplayOrderAsc(member.id!!)).isEmpty()
        }

        @Test
        fun `deleteAllByMemberId로 회원의 링크를 한 번에 삭제한다`() {
            val member = persistMember("links-replace-subject")
            memberProfileLinkRepository.saveAll(
                listOf(
                    MemberProfileLink(memberId = member.id!!, label = "블로그", url = "https://blog.example.com", displayOrder = 0),
                    MemberProfileLink(memberId = member.id!!, label = "포트폴리오", url = "https://portfolio.example.com", displayOrder = 1),
                ),
            )
            memberProfileLinkRepository.flush()

            memberProfileLinkRepository.deleteAllByMemberId(member.id!!)
            memberProfileLinkRepository.flush()
            entityManager.clear()

            assertThat(memberProfileLinkRepository.findAllByMemberIdOrderByDisplayOrderAsc(member.id!!)).isEmpty()
        }

        private fun persistMember(subject: String): Member =
            memberRepository.saveAndFlush(
                Member(oauthProvider = OAuthProvider.DG, oauthSubject = subject, email = "$subject@example.com"),
            )

        companion object {
            @Container
            @ServiceConnection
            @JvmStatic
            val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:18.4-alpine"))
        }
    }
