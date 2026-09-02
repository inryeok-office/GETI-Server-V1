package team.inreok.getiserver.domain.inquiry

import com.redis.testcontainers.RedisContainer
import jakarta.persistence.EntityManagerFactory
import org.assertj.core.api.Assertions.assertThat
import org.hibernate.SessionFactory
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.data.domain.PageRequest
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import team.inreok.getiserver.domain.inquiry.entity.Inquiry
import team.inreok.getiserver.domain.inquiry.entity.type.InquiryType
import team.inreok.getiserver.domain.inquiry.repository.InquiryRepository
import team.inreok.getiserver.domain.inquiry.service.InquiryService
import team.inreok.getiserver.domain.member.entity.Member
import team.inreok.getiserver.domain.member.entity.type.MemberStatus
import team.inreok.getiserver.domain.member.entity.type.OAuthProvider
import team.inreok.getiserver.domain.member.repository.MemberRepository
import java.util.UUID

/**
 * 관리자용 전체 문의 목록의 작성자·담당자 Snapshot 조회가 목록 Row 수에 비례해 Query를 늘리지
 * 않는지 **실제 JDBC Statement 수를 세어** 확인한다.
 *
 * `InquiryServiceImpl.listAdmin`은 `InquiryMemberSnapshotQueryPort.findAllByIds`를 Page 전체에
 * 대해 배치로 한 번만 호출하도록 작성했지만(Row마다 개별 호출하면 N+1), 그 전제가 실제로
 * 지켜지는지는 Mock을 쓰는 Test로는 확인할 수 없다. PR #88의
 * `MemberSearchImageUrlQueryCountIntegrationTest`(학생 검색 이미지 URL 발급 Query 수 고정)와
 * 동일한 방식으로 실측 고정한다.
 */
@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = [
        "app.jwt.secret=inquiry-admin-list-query-count-integration-test-only-jwt-secret-value",
        "app.jwt.access-token-expiration-seconds=1800",
        "app.jwt.refresh-token-expiration-seconds=1209600",
        // Presigned URL 서명은 Local 연산이라 실제 Storage 접속이 필요하지 않다. Bean 생성에
        // 필요한 값만 채운다(MemberSearchImageUrlQueryCountIntegrationTest와 동일한 이유).
        "app.file.storage.bucket=geti-integration-test",
        "app.file.storage.region=us-east-1",
        "app.file.storage.access-key=integration-test-only-access-key",
        "app.file.storage.secret-key=integration-test-only-secret-key",
        // Hibernate Statistics.prepareStatementCount는 SessionFactory 전역 Counter라 이 Test와
        // 무관한 Query도 그대로 더해진다. `@EnableScheduling`이 걸린 실제 운영 Scheduler들이
        // 이 Context에서도 함께 기동하므로(GETI-Server-V1 CI Integration Test에서 반복 관측된
        // 산발적 실패, 근본 원인), before/after 측정 구간에 우연히 끼어들면 Query 수가 흔들려
        // 이 Test가 비결정적으로 깨진다. Statement를 실행하는 Scheduler를 이 Context에서만 끈다.
        "app.program.close-scheduler.interval-ms=86400000",
        "app.discord.bot.sweep-interval-ms=86400000",
        "app.discord.job-notification.retry-interval-ms=86400000",
        "app.search.index-retry-interval-ms=86400000",
        "app.collector.scheduler.cron=-",
    ],
)
class InquiryAdminListQueryCountIntegrationTest {
    @Autowired
    private lateinit var inquiryService: InquiryService

    @Autowired
    private lateinit var inquiryRepository: InquiryRepository

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Autowired
    private lateinit var entityManagerFactory: EntityManagerFactory

    @Test
    fun `목록 결과가 늘어도 작성자·담당자 Snapshot 조회 Query 수는 늘지 않는다`() {
        val developerId = createMember("requester").let { requireNotNull(it.id) }
        createInquiriesWithDistinctAuthorAndAssignee(FEW)

        val statistics = entityManagerFactory.unwrap(SessionFactory::class.java).statistics
        // First invocation can initialize Hibernate query plans/proxies and add a one-time cost to the counter.
        // Warm up the same path before measuring so the assertion covers only the row-count-dependent work.
        listAndCount(statistics, developerId, FEW)
        statistics.clear()
        statistics.isStatisticsEnabled = true

        val fewResult = listAndCount(statistics, developerId, FEW)

        createInquiriesWithDistinctAuthorAndAssignee(MANY - FEW)
        val manyResult = listAndCount(statistics, developerId, MANY)

        // 전제 확인: Page가 실제로 요청한 개수만큼 늘었다. 그렇지 않으면 이 측정 자체가 무의미해진다.
        assertThat(fewResult.rowCount).isEqualTo(FEW)
        assertThat(manyResult.rowCount).isEqualTo(MANY)

        // 핵심: 목록 Row 수가 늘어도 Statement 수는 그대로여야 한다. Row마다 작성자·담당자를
        // 개별 조회한다면 차이가 (MANY - FEW)에 비례해 벌어진다.
        assertThat(manyResult.statements)
            .describedAs(
                "문의 %d건 조회가 %d건 조회보다 Statement를 더 쓰면 안 된다 (few=%d, many=%d)",
                MANY,
                FEW,
                fewResult.statements,
                manyResult.statements,
            ).isEqualTo(fewResult.statements)
    }

    private fun listAndCount(
        statistics: org.hibernate.stat.Statistics,
        requesterId: Long,
        expectedRowCount: Int,
    ): ListMeasurement {
        val before = statistics.prepareStatementCount
        val result =
            inquiryService.listAdmin(
                inquiryType = null,
                status = null,
                query = null,
                assigneeId = null,
                mineOnly = false,
                requesterMemberId = requesterId,
                pageable = PageRequest.of(0, expectedRowCount.coerceAtLeast(1)),
            )
        return ListMeasurement(
            statements = statistics.prepareStatementCount - before,
            rowCount = result.content.size,
        )
    }

    private fun createInquiriesWithDistinctAuthorAndAssignee(count: Int) {
        repeat(count) {
            val author = createMember("author-${UUID.randomUUID()}")
            val assignee = createMember("assignee-${UUID.randomUUID()}")
            inquiryRepository.saveAndFlush(
                Inquiry(
                    authorMemberId = requireNotNull(author.id),
                    type = InquiryType.ETC,
                    title = "Query 수 측정용 문의",
                    content = "N+1 방지 확인용 문의입니다.",
                ).apply {
                    assigneeMemberId = requireNotNull(assignee.id)
                },
            )
        }
    }

    private fun createMember(subject: String): Member =
        memberRepository.saveAndFlush(
            Member(
                oauthProvider = OAuthProvider.DG,
                oauthSubject = subject,
                email = "$subject@example.com",
                status = MemberStatus.ACTIVE,
            ).apply { name = subject },
        )

    private data class ListMeasurement(
        val statements: Long,
        val rowCount: Int,
    )

    companion object {
        private const val FEW = 2
        private const val MANY = 6

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
