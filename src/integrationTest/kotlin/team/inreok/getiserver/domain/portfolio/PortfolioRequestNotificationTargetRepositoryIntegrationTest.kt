package team.inreok.getiserver.domain.portfolio

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import team.inreok.getiserver.domain.member.entity.Member
import team.inreok.getiserver.domain.member.entity.type.OAuthProvider
import team.inreok.getiserver.domain.member.repository.MemberRepository
import team.inreok.getiserver.domain.portfolio.entity.PortfolioRequest
import team.inreok.getiserver.domain.portfolio.entity.PortfolioRequestTarget
import team.inreok.getiserver.domain.portfolio.entity.type.PortfolioRequestStatus
import team.inreok.getiserver.domain.portfolio.repository.PortfolioRequestRepository
import team.inreok.getiserver.domain.portfolio.repository.PortfolioRequestTargetRepository
import java.time.LocalDateTime

/**
 * 알림 이동 대상 판정용 조회(`findNotificationTargetsByIds`)가 실제 PostgreSQL에서 요청 Row와
 * 요청자 대상 여부(`EXISTS` 부분 Query)를 한 번에 돌려주는지 검증한다(Issue #332). Resolver Test는
 * Port를 Mock으로 대체하므로 JPQL 자체는 이 Test에서만 확인된다.
 */
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PortfolioRequestNotificationTargetRepositoryIntegrationTest {
    @Autowired
    private lateinit var requestRepository: PortfolioRequestRepository

    @Autowired
    private lateinit var targetRepository: PortfolioRequestTargetRepository

    @Autowired
    private lateinit var memberRepository: MemberRepository

    private var teacherId: Long = 0L
    private var studentId: Long = 0L
    private var otherStudentId: Long = 0L

    @BeforeEach
    fun setUp() {
        teacherId = saveMember("portfolio-notification-teacher")
        studentId = saveMember("portfolio-notification-student")
        otherStudentId = saveMember("portfolio-notification-other")
    }

    @Test
    fun `요청 상태와 삭제 여부 및 요청자 대상 여부를 한 번에 조회하고 없는 id는 빠진다`() {
        val targeted = saveRequest(PortfolioRequestStatus.PUBLISHED, targetStudentIds = listOf(studentId))
        val notTargeted = saveRequest(PortfolioRequestStatus.CLOSED, targetStudentIds = listOf(otherStudentId))
        val deleted =
            saveRequest(PortfolioRequestStatus.PUBLISHED, targetStudentIds = listOf(studentId), deleted = true)
        val draft = saveRequest(PortfolioRequestStatus.DRAFT, targetStudentIds = emptyList())

        val rows =
            requestRepository
                .findNotificationTargetsByIds(listOf(targeted, notTargeted, deleted, draft, 999_999L), studentId)
                .associateBy { it.requestId }

        assertThat(rows.keys).containsExactlyInAnyOrder(targeted, notTargeted, deleted, draft)
        assertThat(rows.getValue(targeted).targetedToViewer).isTrue
        assertThat(rows.getValue(targeted).status).isEqualTo(PortfolioRequestStatus.PUBLISHED)
        assertThat(rows.getValue(targeted).deletedAt).isNull()
        assertThat(rows.getValue(notTargeted).targetedToViewer).isFalse
        assertThat(rows.getValue(notTargeted).status).isEqualTo(PortfolioRequestStatus.CLOSED)
        assertThat(rows.getValue(deleted).deletedAt).isNotNull
        assertThat(rows.getValue(draft).status).isEqualTo(PortfolioRequestStatus.DRAFT)
        assertThat(rows.getValue(draft).targetedToViewer).isFalse
    }

    private fun saveMember(subject: String): Long =
        requireNotNull(
            memberRepository
                .saveAndFlush(
                    Member(
                        oauthProvider = OAuthProvider.DG,
                        oauthSubject = subject,
                        email = "$subject@example.com",
                    ),
                ).id,
        )

    private fun saveRequest(
        status: PortfolioRequestStatus,
        targetStudentIds: List<Long>,
        deleted: Boolean = false,
    ): Long {
        val request =
            requestRepository.saveAndFlush(
                PortfolioRequest(
                    createdByMemberId = teacherId,
                    title = "포트폴리오 수합 ${status.name}",
                    dueAt = LocalDateTime.of(2026, 12, 31, 23, 59),
                    status = status,
                ).also { if (deleted) it.deletedAt = LocalDateTime.now() },
            )
        val requestId = requireNotNull(request.id)
        targetStudentIds.forEach { targetRepository.saveAndFlush(PortfolioRequestTarget(requestId, it)) }
        return requestId
    }

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:18-alpine")
    }
}
