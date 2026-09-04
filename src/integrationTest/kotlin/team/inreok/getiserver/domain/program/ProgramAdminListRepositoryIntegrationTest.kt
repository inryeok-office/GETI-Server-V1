package team.inreok.getiserver.domain.program

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.data.domain.PageRequest
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import team.inreok.getiserver.domain.member.entity.Member
import team.inreok.getiserver.domain.member.entity.type.OAuthProvider
import team.inreok.getiserver.domain.member.repository.MemberRepository
import team.inreok.getiserver.domain.program.entity.Program
import team.inreok.getiserver.domain.program.entity.type.ProgramStatus
import team.inreok.getiserver.domain.program.entity.type.ProgramType
import team.inreok.getiserver.domain.program.repository.ProgramRepository
import java.time.LocalDateTime

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ProgramAdminListRepositoryIntegrationTest {
    @Autowired
    private lateinit var programRepository: ProgramRepository

    @Autowired
    private lateinit var memberRepository: MemberRepository

    private var createdByMemberId: Long = 0L

    @BeforeEach
    fun setUp() {
        createdByMemberId =
            requireNotNull(
                memberRepository
                    .saveAndFlush(
                        Member(
                            oauthProvider = OAuthProvider.DG,
                            oauthSubject = "program-admin-list",
                            email = "program-admin-list@example.com",
                        ),
                    ).id,
            )
    }

    @Test
    fun `관리자 목록은 PostgreSQL에서 nullable query와 상태 정책 및 안정 정렬을 적용한다`() {
        save("초안", ProgramStatus.DRAFT, 1)
        save("공개 특강", ProgramStatus.PUBLISHED, 2)
        save("마감", ProgramStatus.CLOSED, 3)
        save("삭제", ProgramStatus.DELETED, 4).also {
            it.deletedAt = LocalDateTime.now()
            programRepository.saveAndFlush(it)
        }

        val defaultPage = programRepository.searchForAdmin(null, null, PageRequest.of(0, 20))
        val repeatedNullQueryPage = programRepository.searchForAdmin(null, null, PageRequest.of(0, 20))
        val deletedPage = programRepository.searchForAdmin(null, ProgramStatus.DELETED, PageRequest.of(0, 20))
        val searchPage = programRepository.searchForAdmin("특강", ProgramStatus.PUBLISHED, PageRequest.of(0, 20))

        assertThat(defaultPage.content.map { it.status })
            .containsExactly(ProgramStatus.CLOSED, ProgramStatus.PUBLISHED, ProgramStatus.DRAFT)
        assertThat(repeatedNullQueryPage.totalElements).isEqualTo(3)
        assertThat(deletedPage.content.map { it.title }).containsExactly("삭제")
        assertThat(searchPage.content.map { it.title }).containsExactly("공개 특강")
    }

    private fun save(
        title: String,
        status: ProgramStatus,
        createdAtOffset: Long,
    ): Program =
        Program(
            createdByMemberId = createdByMemberId,
            type = ProgramType.SPECIAL_LECTURE,
            title = title,
            status = status,
        ).also {
            it.createdAt = LocalDateTime.of(2026, 1, 1, 0, 0).plusSeconds(createdAtOffset)
            programRepository.saveAndFlush(it)
        }

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:18-alpine")
    }
}
