package team.inreok.getiserver.domain.member

import com.redis.testcontainers.RedisContainer
import jakarta.persistence.EntityManagerFactory
import org.assertj.core.api.Assertions.assertThat
import org.hibernate.SessionFactory
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.data.domain.PageRequest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import team.inreok.getiserver.domain.file.entity.StoredFile
import team.inreok.getiserver.domain.file.entity.type.FileOwnerType
import team.inreok.getiserver.domain.file.entity.type.FilePurpose
import team.inreok.getiserver.domain.file.repository.StoredFileRepository
import team.inreok.getiserver.domain.member.entity.Member
import team.inreok.getiserver.domain.member.entity.MemberRole
import team.inreok.getiserver.domain.member.entity.MemberRoleId
import team.inreok.getiserver.domain.member.entity.type.MemberStatus
import team.inreok.getiserver.domain.member.entity.type.OAuthProvider
import team.inreok.getiserver.domain.member.entity.type.RoleType
import team.inreok.getiserver.domain.member.repository.MemberRepository
import team.inreok.getiserver.domain.member.repository.MemberRoleRepository
import team.inreok.getiserver.domain.member.service.MemberSearchService
import java.time.LocalDateTime
import java.util.UUID

/**
 * 학생 검색 목록의 프로필 이미지 URL 발급이 회원 수에 비례해 Query를 늘리지 않는지 **실제 JDBC
 * Statement 수를 세어** 확인한다.
 *
 * `MemberSearchServiceImpl`은 `FileUrlPort.presignedImageUrls`를 배치로 한 번만 호출하지만, 그
 * 안쪽에서 파일마다 `FileAccessResolver.canAccess` -> `MemberProfileImageAccessChecker.canDownload`
 * -> `memberRepository.findById(ownerId)`가 호출된다. 이 조회가 JPA 1차 캐시에서 해결되는지
 * (검색이 이미 managed Member를 반환했으므로) 아니면 실제 SELECT로 나가는지는 Mock을 쓰는
 * `MemberSearchServiceTest`로는 알 수 없다 -- PR #88 리뷰에서 판단이 갈린 지점이라 측정으로
 * 고정한다.
 *
 * **이 Test는 "검색이 managed Entity를 반환한다"는 전제를 지킨다.** 나중에 성능 목적으로
 * `MemberRepository.search`를 DTO Projection으로 바꾸면 1차 캐시가 비어 회원 수만큼 SELECT가
 * 늘고 이 Test가 깨진다. 그때가 `FileAccessChecker`에 배치 판정을 도입할 시점이다.
 */
@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = [
        "app.jwt.secret=member-search-query-count-integration-test-only-jwt-secret-value",
        "app.jwt.access-token-expiration-seconds=1800",
        "app.jwt.refresh-token-expiration-seconds=1209600",
        // Presigned URL 서명은 Local 연산이라 실제 Storage 접속이 필요하지 않다. Bean 생성에
        // 필요한 값만 채운다(app.file.storage는 Profile별 파일에만 있어 여기서 지정한다).
        "app.file.storage.bucket=geti-integration-test",
        "app.file.storage.region=us-east-1",
        "app.file.storage.access-key=integration-test-only-access-key",
        "app.file.storage.secret-key=integration-test-only-secret-key",
    ],
)
class MemberSearchImageUrlQueryCountIntegrationTest {
    @Autowired
    private lateinit var memberSearchService: MemberSearchService

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Autowired
    private lateinit var memberRoleRepository: MemberRoleRepository

    @Autowired
    private lateinit var storedFileRepository: StoredFileRepository

    @Autowired
    private lateinit var entityManagerFactory: EntityManagerFactory

    @Test
    fun `검색 결과가 늘어도 프로필 이미지 URL 발급 Query 수는 늘지 않는다`() {
        val requesterId = createStudent("requester").let { requireNotNull(it.id) }
        val fewName = "쿼리카운트${UUID.randomUUID().toString().take(8)}"
        val manyName = "쿼리카운트${UUID.randomUUID().toString().take(8)}"
        createStudentsWithProfileImage(fewName, count = FEW)
        createStudentsWithProfileImage(manyName, count = MANY)

        val statistics = entityManagerFactory.unwrap(SessionFactory::class.java).statistics
        statistics.isStatisticsEnabled = true

        val fewResult = searchAndCount(statistics, requesterId, fewName)
        val manyResult = searchAndCount(statistics, requesterId, manyName)

        // 전제 확인: 두 검색 모두 실제로 URL을 발급했다. URL이 하나도 안 나가면 권한 판정 경로를
        // 타지 않아 이 측정 자체가 무의미해진다.
        assertThat(fewResult.urlCount).isEqualTo(FEW)
        assertThat(manyResult.urlCount).isEqualTo(MANY)

        // 핵심: 회원 수가 늘어도 Statement 수는 그대로여야 한다. 회원마다 findById가 실제
        // SELECT로 나간다면 차이가 (MANY - FEW)만큼 벌어진다.
        assertThat(manyResult.statements)
            .describedAs(
                "회원 %d명 검색이 %d명 검색보다 Statement를 더 쓰면 안 된다 (few=%d, many=%d)",
                MANY,
                FEW,
                fewResult.statements,
                manyResult.statements,
            ).isEqualTo(fewResult.statements)
    }

    /**
     * 교사·개발자 예외(Issue #114)가 Query를 늘리지 않는지 측정한다. 요청자 Role을
     * `MemberRoleRepository`로 조회하는 구현이었다면 파생 Query라 1차 캐시가 걸리지 않아 파일마다
     * SELECT가 나갔을 것이고, 이 Test가 그 회귀를 잡는다.
     */
    @Test
    fun `교사가 검색해도 비공개 학생 수에 따라 Query 수는 늘지 않는다`() {
        val teacherId = createTeacher("teacher-${UUID.randomUUID().toString().take(8)}").let { requireNotNull(it.id) }
        val fewName = "교사쿼리${UUID.randomUUID().toString().take(8)}"
        val manyName = "교사쿼리${UUID.randomUUID().toString().take(8)}"
        createStudentsWithProfileImage(fewName, count = FEW, profilePublic = false)
        createStudentsWithProfileImage(manyName, count = MANY, profilePublic = false)

        authenticateAsTeacher(teacherId)

        val statistics = entityManagerFactory.unwrap(SessionFactory::class.java).statistics
        statistics.isStatisticsEnabled = true

        val fewResult = searchAndCount(statistics, teacherId, fewName)
        val manyResult = searchAndCount(statistics, teacherId, manyName)

        // 전제 확인: 대상이 모두 비공개인데도 URL이 발급됐다면 교사 예외가 실제로 동작한 것이다.
        assertThat(fewResult.urlCount).isEqualTo(FEW)
        assertThat(manyResult.urlCount).isEqualTo(MANY)

        assertThat(manyResult.statements)
            .describedAs(
                "교사가 회원 %d명을 검색할 때 %d명 검색보다 Statement를 더 쓰면 안 된다 (few=%d, many=%d)",
                MANY,
                FEW,
                fewResult.statements,
                manyResult.statements,
            ).isEqualTo(fewResult.statements)
    }

    // SecurityContextHolder는 ThreadLocal이라 정리하지 않으면 같은 Thread를 재사용하는 다른 Test로
    // 교사 권한이 새어 나간다.
    @AfterEach
    fun clearSecurityContext() {
        SecurityContextHolder.clearContext()
    }

    private fun authenticateAsTeacher(memberId: Long) {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(
                memberId,
                null,
                listOf(SimpleGrantedAuthority("ROLE_${RoleType.TEACHER}")),
            )
    }

    private fun searchAndCount(
        statistics: org.hibernate.stat.Statistics,
        requesterId: Long,
        name: String,
    ): SearchMeasurement {
        val before = statistics.prepareStatementCount
        val result =
            memberSearchService.search(
                requesterId = requesterId,
                name = name,
                academicStatus = null,
                cohort = null,
                department = null,
                majorId = null,
                techStackId = null,
                pageable = PageRequest.of(0, PAGE_SIZE),
            )
        return SearchMeasurement(
            statements = statistics.prepareStatementCount - before,
            urlCount = result.content.count { it.profileImageUrl != null },
        )
    }

    private fun createStudentsWithProfileImage(
        name: String,
        count: Int,
        profilePublic: Boolean = true,
    ) {
        repeat(count) { index ->
            val member = createStudent("$name-$index", displayName = name, profilePublic = profilePublic)
            val memberId = requireNotNull(member.id)
            val file =
                storedFileRepository.saveAndFlush(
                    StoredFile(
                        purpose = FilePurpose.PROFILE_IMAGE,
                        objectKey = "PROFILE_IMAGE/2026/08/${UUID.randomUUID()}",
                        originalName = "profile.png",
                        contentType = "image/png",
                        sizeBytes = 100L,
                        uploaderMemberId = memberId,
                        extension = "png",
                    ).apply {
                        markUploaded()
                        linkTo(FileOwnerType.MEMBER, memberId, LocalDateTime.now())
                    },
                )
            member.profileImageFileId = requireNotNull(file.id)
            memberRepository.saveAndFlush(member)
        }
    }

    private fun createStudent(
        subject: String,
        displayName: String = subject,
        // 공개 프로필이라야 다른 학생에게 URL이 발급되어 권한 판정 경로를 실제로 탄다. 교사 경로
        // 측정에서는 비공개로 만들어, URL이 나갔다는 사실 자체가 교사 예외가 동작했다는 근거가 된다.
        profilePublic: Boolean = true,
    ): Member = createMember(subject, RoleType.STUDENT, displayName, profilePublic)

    private fun createTeacher(subject: String): Member =
        createMember(subject, RoleType.TEACHER, subject, profilePublic = true)

    private fun createMember(
        subject: String,
        role: RoleType,
        displayName: String,
        profilePublic: Boolean,
    ): Member {
        val member =
            memberRepository.saveAndFlush(
                Member(
                    oauthProvider = OAuthProvider.DG,
                    oauthSubject = subject,
                    email = "$subject@example.com",
                    status = MemberStatus.ACTIVE,
                    profilePublic = profilePublic,
                ).apply { name = displayName },
            )
        memberRoleRepository.saveAndFlush(MemberRole(MemberRoleId(requireNotNull(member.id), role)))
        return member
    }

    private data class SearchMeasurement(
        val statements: Long,
        val urlCount: Int,
    )

    companion object {
        private const val FEW = 2
        private const val MANY = 6
        private const val PAGE_SIZE = 50

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
