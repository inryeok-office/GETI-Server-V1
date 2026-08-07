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
import org.springframework.data.domain.PageRequest
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import team.inreok.getiserver.domain.member.entity.Member
import team.inreok.getiserver.domain.member.entity.type.OAuthProvider
import team.inreok.getiserver.domain.member.repository.MemberRepository
import team.inreok.getiserver.domain.notification.entity.Notification
import team.inreok.getiserver.domain.notification.entity.type.NotificationTargetType
import team.inreok.getiserver.domain.notification.entity.type.NotificationType
import team.inreok.getiserver.domain.notification.repository.NotificationRepository
import java.time.LocalDateTime

/**
 * 인앱 알림 조회·읽음 처리 Query가 실제 PostgreSQL(V16 Migration 포함)에서 의도대로 동작하는지
 * 검증한다. `ddl-auto=validate`가 함께 돌아 `Notification` Entity Mapping이 V16 이후 실제
 * Schema(target_type/target_id/updated_at)와 일치하는지도 확인된다.
 */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration::class)
class NotificationRepositoryIntegrationTest
    @Autowired
    constructor(
        private val notificationRepository: NotificationRepository,
        private val memberRepository: MemberRepository,
    ) {
        private var ownerId: Long = 0
        private var otherId: Long = 0

        @BeforeEach
        fun setUp() {
            notificationRepository.deleteAll()
            notificationRepository.flush()
            memberRepository.deleteAll()
            memberRepository.flush()
            ownerId = persistMember("owner").id!!
            otherId = persistMember("other").id!!
        }

        @Test
        fun `내 알림만 조회하고 다른 사용자의 알림은 섞이지 않는다`() {
            persist(recipientMemberId = ownerId, title = "내 알림")
            persist(recipientMemberId = otherId, title = "남의 알림")

            val page = notificationRepository.findMyNotifications(ownerId, null, null, PageRequest.of(0, 20))

            assertThat(page.totalElements).isEqualTo(1)
            assertThat(page.content.single().title).isEqualTo("내 알림")
        }

        @Test
        fun `목록은 최신순으로 정렬되고 같은 시각이면 id가 큰 것이 먼저다`() {
            val sameMoment = LocalDateTime.of(2026, 8, 7, 10, 0)
            val older = persist(recipientMemberId = ownerId, title = "이전", createdAt = sameMoment.minusHours(1))
            val first = persist(recipientMemberId = ownerId, title = "같은시각-1", createdAt = sameMoment)
            val second = persist(recipientMemberId = ownerId, title = "같은시각-2", createdAt = sameMoment)

            val page = notificationRepository.findMyNotifications(ownerId, null, null, PageRequest.of(0, 20))

            assertThat(page.content.map { it.id }).containsExactly(second.id, first.id, older.id)
        }

        @Test
        fun `읽음 여부로 필터링한다`() {
            persist(recipientMemberId = ownerId, title = "안읽음", isRead = false)
            persist(recipientMemberId = ownerId, title = "읽음", isRead = true, readAt = LocalDateTime.now())

            val unread = notificationRepository.findMyNotifications(ownerId, false, null, PageRequest.of(0, 20))
            val read = notificationRepository.findMyNotifications(ownerId, true, null, PageRequest.of(0, 20))

            assertThat(unread.content.single().title).isEqualTo("안읽음")
            assertThat(read.content.single().title).isEqualTo("읽음")
        }

        @Test
        fun `알림 종류로 필터링한다`() {
            persist(recipientMemberId = ownerId, title = "프로그램", type = NotificationType.PROGRAM_PUBLISHED)
            persist(recipientMemberId = ownerId, title = "공고", type = NotificationType.JOB_PUBLISHED)

            val page =
                notificationRepository.findMyNotifications(
                    ownerId,
                    null,
                    NotificationType.JOB_PUBLISHED,
                    PageRequest.of(0, 20),
                )

            assertThat(page.content.single().title).isEqualTo("공고")
        }

        @Test
        fun `읽음 여부와 종류 필터를 함께 적용한다`() {
            persist(recipientMemberId = ownerId, title = "대상", type = NotificationType.JOB_PUBLISHED, isRead = false)
            persist(
                recipientMemberId = ownerId,
                title = "종류 불일치",
                type = NotificationType.PROGRAM_PUBLISHED,
                isRead = false,
            )
            persist(
                recipientMemberId = ownerId,
                title = "이미 읽음",
                type = NotificationType.JOB_PUBLISHED,
                isRead = true,
                readAt = LocalDateTime.now(),
            )

            val page =
                notificationRepository.findMyNotifications(
                    ownerId,
                    false,
                    NotificationType.JOB_PUBLISHED,
                    PageRequest.of(0, 20),
                )

            assertThat(page.content.single().title).isEqualTo("대상")
        }

        @Test
        fun `삭제된 알림은 목록과 읽지 않은 개수에서 모두 제외된다`() {
            persist(recipientMemberId = ownerId, title = "살아있음")
            persist(recipientMemberId = ownerId, title = "삭제됨", deletedAt = LocalDateTime.now())

            val page = notificationRepository.findMyNotifications(ownerId, null, null, PageRequest.of(0, 20))

            assertThat(page.content.single().title).isEqualTo("살아있음")
            assertThat(
                notificationRepository.countByRecipientMemberIdAndIsReadFalseAndDeletedAtIsNull(ownerId),
            ).isEqualTo(1)
        }

        @Test
        fun `읽지 않은 알림 개수는 본인 것만 센다`() {
            persist(recipientMemberId = ownerId, isRead = false)
            persist(recipientMemberId = ownerId, isRead = false)
            persist(recipientMemberId = ownerId, isRead = true, readAt = LocalDateTime.now())
            persist(recipientMemberId = otherId, isRead = false)

            assertThat(
                notificationRepository.countByRecipientMemberIdAndIsReadFalseAndDeletedAtIsNull(ownerId),
            ).isEqualTo(2)
        }

        @Test
        fun `전체 읽음 처리는 본인의 읽지 않은 알림만 갱신하고 건수를 반환한다`() {
            val mine1 = persist(recipientMemberId = ownerId, isRead = false)
            val mine2 = persist(recipientMemberId = ownerId, isRead = false)
            val alreadyRead =
                persist(
                    recipientMemberId = ownerId,
                    isRead = true,
                    readAt = LocalDateTime.of(2026, 8, 1, 9, 0),
                )
            val others = persist(recipientMemberId = otherId, isRead = false)
            val readAt = LocalDateTime.of(2026, 8, 7, 12, 0)

            val updatedCount = notificationRepository.markAllAsRead(ownerId, readAt)
            notificationRepository.flush()

            assertThat(updatedCount).isEqualTo(2)
            // 갱신된 알림은 모두 같은 readAt과 updatedAt을 갖는다(Bulk UPDATE는 @UpdateTimestamp를
            // 거치지 않으므로 Query가 직접 채운다).
            listOf(mine1.id!!, mine2.id!!).forEach { id ->
                val updated = notificationRepository.findById(id).orElseThrow()
                assertThat(updated.isRead).isTrue
                assertThat(updated.readAt).isEqualTo(readAt)
                assertThat(updated.updatedAt).isEqualTo(readAt)
            }
            // 이미 읽은 알림의 시각은 덮어쓰지 않는다.
            assertThat(notificationRepository.findById(alreadyRead.id!!).orElseThrow().readAt)
                .isEqualTo(LocalDateTime.of(2026, 8, 1, 9, 0))
            // 다른 사용자의 알림은 건드리지 않는다.
            assertThat(notificationRepository.findById(others.id!!).orElseThrow().isRead).isFalse
        }

        @Test
        fun `읽을 알림이 없으면 전체 읽음 처리가 0을 반환한다`() {
            persist(recipientMemberId = ownerId, isRead = true, readAt = LocalDateTime.now())

            assertThat(notificationRepository.markAllAsRead(ownerId, LocalDateTime.now())).isZero
        }

        @Test
        fun `대상 유형과 대상 ID가 V16 Column에 저장되고 그대로 조회된다`() {
            val saved =
                persist(
                    recipientMemberId = ownerId,
                    targetType = NotificationTargetType.PROGRAM,
                    targetId = 123L,
                )

            val found = notificationRepository.findById(saved.id!!).orElseThrow()

            assertThat(found.targetType).isEqualTo(NotificationTargetType.PROGRAM)
            assertThat(found.targetId).isEqualTo(123L)
            assertThat(found.createdAt).isNotNull
            assertThat(found.updatedAt).isNotNull
        }

        @Test
        fun `대상이 없는 알림도 저장할 수 있다`() {
            val saved = persist(recipientMemberId = ownerId, targetType = null, targetId = null)

            val found = notificationRepository.findById(saved.id!!).orElseThrow()

            assertThat(found.targetType).isNull()
            assertThat(found.targetId).isNull()
        }

        private fun persist(
            recipientMemberId: Long,
            title: String = "제목",
            type: NotificationType = NotificationType.PROGRAM_PUBLISHED,
            isRead: Boolean = false,
            readAt: LocalDateTime? = null,
            targetType: NotificationTargetType? = NotificationTargetType.PROGRAM,
            targetId: Long? = 100L,
            createdAt: LocalDateTime? = null,
            deletedAt: LocalDateTime? = null,
        ): Notification =
            notificationRepository.saveAndFlush(
                Notification(
                    recipientMemberId = recipientMemberId,
                    type = type,
                    title = title,
                    content = "내용",
                    isRead = isRead,
                ).apply {
                    this.targetType = targetType
                    this.targetId = targetId
                    this.readAt = readAt
                    this.deletedAt = deletedAt
                    // @CreationTimestamp가 채운 값을 특정 시각으로 고정해야 정렬을 검증할 수 있다.
                    createdAt?.let { this.createdAt = it }
                },
            )

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
