package team.inreok.getiserver.domain.notification.service.impl

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.isNull
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import team.inreok.getiserver.domain.notification.dto.NotificationCreateCommand
import team.inreok.getiserver.domain.notification.entity.Notification
import team.inreok.getiserver.domain.notification.entity.type.NotificationTargetType
import team.inreok.getiserver.domain.notification.entity.type.NotificationTargetUnavailableReason
import team.inreok.getiserver.domain.notification.entity.type.NotificationType
import team.inreok.getiserver.domain.notification.exception.NotificationAccessDeniedException
import team.inreok.getiserver.domain.notification.exception.NotificationNotFoundException
import team.inreok.getiserver.domain.notification.repository.NotificationRepository
import team.inreok.getiserver.domain.notification.service.NotificationTargetAvailability
import team.inreok.getiserver.domain.notification.service.NotificationTargetRef
import team.inreok.getiserver.domain.notification.service.NotificationTargetResolver
import java.time.LocalDateTime
import java.util.Optional

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationServiceImplTest {
    @Mock
    private lateinit var notificationRepository: NotificationRepository

    @Mock
    private lateinit var notificationTargetResolver: NotificationTargetResolver

    private val ownerMemberId = 1L
    private val otherMemberId = 2L

    private fun service() = NotificationServiceImpl(notificationRepository, notificationTargetResolver)

    private fun notification(
        id: Long,
        recipientMemberId: Long = ownerMemberId,
        isRead: Boolean = false,
        readAt: LocalDateTime? = null,
        targetType: NotificationTargetType? = NotificationTargetType.PROGRAM,
        targetId: Long? = 100L,
        deletedAt: LocalDateTime? = null,
    ) = Notification(
        recipientMemberId = recipientMemberId,
        type = NotificationType.PROGRAM_PUBLISHED,
        title = "제목",
        content = "내용",
        isRead = isRead,
    ).apply {
        this.id = id
        this.targetType = targetType
        this.targetId = targetId
        this.readAt = readAt
        this.deletedAt = deletedAt
        this.createdAt = LocalDateTime.of(2026, 8, 7, 10, 0)
    }

    // Kotlin non-null 파라미터에 bare any()를 쓰면 null 반환으로 NPE가 나므로 Elvis로 기본값을
    // 채운다(ProgramControllerTest.anyActionRequest()와 같은 이유).
    private fun anyPageable(): Pageable = any(Pageable::class.java) ?: Pageable.unpaged()

    private fun anyDateTime(): LocalDateTime = any(LocalDateTime::class.java) ?: LocalDateTime.now()

    private val availableTarget =
        NotificationTargetAvailability(available = true, reason = null, deepLink = "/programs/100")

    // ---------- 생성 ----------

    @Test
    fun `알림을 생성하고 생성된 ID를 반환한다`() {
        val captor = ArgumentCaptor.forClass(Notification::class.java)
        given(notificationRepository.save(captor.capture() ?: notification(1L)))
            .willAnswer { invocation -> invocation.getArgument<Notification>(0).apply { id = 7L } }

        val createdId =
            service().create(
                NotificationCreateCommand(
                    recipientMemberId = ownerMemberId,
                    type = NotificationType.PROGRAM_PUBLISHED,
                    title = "새 프로그램",
                    content = "모집이 시작되었습니다.",
                    targetType = NotificationTargetType.PROGRAM,
                    targetId = 100L,
                ),
            )

        assertThat(createdId).isEqualTo(7L)
        with(captor.value) {
            assertThat(recipientMemberId).isEqualTo(ownerMemberId)
            assertThat(type).isEqualTo(NotificationType.PROGRAM_PUBLISHED)
            assertThat(title).isEqualTo("새 프로그램")
            assertThat(targetType).isEqualTo(NotificationTargetType.PROGRAM)
            assertThat(targetId).isEqualTo(100L)
            // 생성 직후에는 항상 읽지 않은 상태다.
            assertThat(isRead).isFalse
            assertThat(readAt).isNull()
        }
    }

    @Test
    fun `대상이 없는 알림도 생성할 수 있다`() {
        val captor = ArgumentCaptor.forClass(Notification::class.java)
        given(notificationRepository.save(captor.capture() ?: notification(1L)))
            .willAnswer { invocation -> invocation.getArgument<Notification>(0).apply { id = 8L } }

        service().create(
            NotificationCreateCommand(
                recipientMemberId = ownerMemberId,
                type = NotificationType.SYSTEM,
                title = "공지",
                content = "점검 안내",
            ),
        )

        assertThat(captor.value.targetType).isNull()
        assertThat(captor.value.targetId).isNull()
    }

    // ---------- 목록 ----------

    @Test
    fun `목록은 대상 접근 가능 여부와 deepLink를 계산해서 내려준다`() {
        val target = NotificationTargetRef(NotificationTargetType.PROGRAM, 100L)
        givenPage(listOf(notification(1L)))
        given(notificationTargetResolver.resolveAll(setOf(target), ownerMemberId))
            .willReturn(mapOf(target to availableTarget))

        val response = service().list(ownerMemberId, null, null, PageRequest.of(0, 20))

        assertThat(response.content).hasSize(1)
        with(response.content.first()) {
            assertThat(notificationId).isEqualTo(1L)
            assertThat(targetAvailable).isTrue
            assertThat(targetUnavailableReason).isNull()
            assertThat(deepLink).isEqualTo("/programs/100")
        }
        assertThat(response.page).isEqualTo(0)
        assertThat(response.size).isEqualTo(20)
        assertThat(response.totalElements).isEqualTo(1L)
        assertThat(response.first).isTrue
        assertThat(response.last).isTrue
    }

    @Test
    fun `삭제된 원본을 가리키는 알림도 목록에 남고 사유가 함께 내려간다`() {
        val target = NotificationTargetRef(NotificationTargetType.PROGRAM, 100L)
        givenPage(listOf(notification(1L)))
        given(notificationTargetResolver.resolveAll(setOf(target), ownerMemberId))
            .willReturn(
                mapOf(
                    target to
                        NotificationTargetAvailability(
                            available = false,
                            reason = NotificationTargetUnavailableReason.DELETED,
                            deepLink = null,
                        ),
                ),
            )

        val response = service().list(ownerMemberId, null, null, PageRequest.of(0, 20))

        assertThat(response.content).hasSize(1)
        with(response.content.first()) {
            assertThat(targetAvailable).isFalse
            assertThat(targetUnavailableReason).isEqualTo(NotificationTargetUnavailableReason.DELETED)
            assertThat(deepLink).isNull()
        }
    }

    @Test
    fun `대상이 없는 알림은 이동 불가로 내려간다`() {
        givenPage(listOf(notification(1L, targetType = null, targetId = null)))
        given(notificationTargetResolver.resolveAll(emptySet(), ownerMemberId)).willReturn(emptyMap())

        val response = service().list(ownerMemberId, null, null, PageRequest.of(0, 20))

        with(response.content.first()) {
            assertThat(targetType).isNull()
            assertThat(targetId).isNull()
            assertThat(targetAvailable).isFalse
            assertThat(targetUnavailableReason).isNull()
            assertThat(deepLink).isNull()
        }
    }

    @Test
    fun `읽음 여부와 종류 필터를 Repository에 그대로 전달한다`() {
        givenPage(emptyList())
        given(notificationTargetResolver.resolveAll(emptySet(), ownerMemberId)).willReturn(emptyMap())

        service().list(ownerMemberId, false, NotificationType.JOB_PUBLISHED, PageRequest.of(1, 50))

        verify(notificationRepository).findMyNotifications(
            memberId = ownerMemberId,
            isRead = false,
            type = NotificationType.JOB_PUBLISHED,
            pageable = PageRequest.of(1, 50),
        )
    }

    @Test
    fun `클라이언트가 보낸 정렬은 무시한다`() {
        givenPage(emptyList())
        given(notificationTargetResolver.resolveAll(emptySet(), ownerMemberId)).willReturn(emptyMap())

        service().list(ownerMemberId, null, null, PageRequest.of(0, 20, Sort.by("title").ascending()))

        // Sort를 그대로 넘기면 JPQL의 ORDER BY(createdAt DESC, id DESC)와 충돌하므로 제거해야 한다.
        val captor = ArgumentCaptor.forClass(Pageable::class.java)
        verify(notificationRepository).findMyNotifications(
            memberId = anyLong(),
            isRead = isNull(),
            type = isNull(),
            pageable = captor.capture() ?: Pageable.unpaged(),
        )
        assertThat(captor.value.sort.isSorted).isFalse
        assertThat(captor.value.pageNumber).isEqualTo(0)
        assertThat(captor.value.pageSize).isEqualTo(20)
    }

    // ---------- 읽지 않은 개수 ----------

    @Test
    fun `읽지 않은 알림 개수를 반환한다`() {
        given(notificationRepository.countByRecipientMemberIdAndIsReadFalseAndDeletedAtIsNull(ownerMemberId))
            .willReturn(3L)

        assertThat(service().countUnread(ownerMemberId).unreadCount).isEqualTo(3L)
    }

    @Test
    fun `읽지 않은 알림이 없으면 0을 반환한다`() {
        given(notificationRepository.countByRecipientMemberIdAndIsReadFalseAndDeletedAtIsNull(ownerMemberId))
            .willReturn(0L)

        assertThat(service().countUnread(ownerMemberId).unreadCount).isZero
    }

    // ---------- 단일 읽음 ----------

    @Test
    fun `읽지 않은 알림을 읽음 처리한다`() {
        val target = notification(1L)
        given(notificationRepository.findById(1L)).willReturn(Optional.of(target))

        val response = service().markAsRead(ownerMemberId, 1L)

        assertThat(response.notificationId).isEqualTo(1L)
        assertThat(response.isRead).isTrue
        assertThat(target.isRead).isTrue
        assertThat(target.readAt).isNotNull
    }

    @Test
    fun `이미 읽은 알림을 다시 읽음 처리해도 처음 읽은 시각이 유지된다`() {
        val firstReadAt = LocalDateTime.of(2026, 8, 1, 9, 0)
        val target = notification(1L, isRead = true, readAt = firstReadAt)
        given(notificationRepository.findById(1L)).willReturn(Optional.of(target))

        val response = service().markAsRead(ownerMemberId, 1L)

        assertThat(response.isRead).isTrue
        assertThat(response.readAt).isEqualTo(firstReadAt)
        assertThat(target.readAt).isEqualTo(firstReadAt)
    }

    @Test
    fun `없는 알림을 읽음 처리하면 NOTIFICATION_NOT_FOUND다`() {
        given(notificationRepository.findById(99L)).willReturn(Optional.empty())

        assertThatThrownBy { service().markAsRead(ownerMemberId, 99L) }
            .isInstanceOf(NotificationNotFoundException::class.java)
    }

    @Test
    fun `삭제된 알림을 읽음 처리하면 NOTIFICATION_NOT_FOUND다`() {
        val target = notification(1L, deletedAt = LocalDateTime.of(2026, 8, 5, 9, 0))
        given(notificationRepository.findById(1L)).willReturn(Optional.of(target))

        assertThatThrownBy { service().markAsRead(ownerMemberId, 1L) }
            .isInstanceOf(NotificationNotFoundException::class.java)

        // 삭제된 알림은 목록에도 나오지 않으므로 읽음 상태를 바꾸지 않는다.
        assertThat(target.isRead).isFalse
        assertThat(target.readAt).isNull()
    }

    @Test
    fun `삭제된 타인의 알림은 403이 아니라 404로 감춘다`() {
        // 소유권보다 삭제 여부를 먼저 확인해야 타인 알림의 존재 여부가 403으로 드러나지 않는다.
        val target =
            notification(
                1L,
                recipientMemberId = otherMemberId,
                deletedAt = LocalDateTime.of(2026, 8, 5, 9, 0),
            )
        given(notificationRepository.findById(1L)).willReturn(Optional.of(target))

        assertThatThrownBy { service().markAsRead(ownerMemberId, 1L) }
            .isInstanceOf(NotificationNotFoundException::class.java)
    }

    @Test
    fun `다른 사용자의 알림을 읽음 처리하면 NOTIFICATION_ACCESS_DENIED다`() {
        val target = notification(1L, recipientMemberId = otherMemberId)
        given(notificationRepository.findById(1L)).willReturn(Optional.of(target))

        assertThatThrownBy { service().markAsRead(ownerMemberId, 1L) }
            .isInstanceOf(NotificationAccessDeniedException::class.java)

        // 남의 알림 상태를 바꾸지 않는다.
        assertThat(target.isRead).isFalse
        assertThat(target.readAt).isNull()
    }

    @Test
    fun `다른 사용자의 알림이라는 사실을 예외 Message로 알려주지 않는다`() {
        val target = notification(1L, recipientMemberId = otherMemberId)
        given(notificationRepository.findById(1L)).willReturn(Optional.of(target))

        assertThatThrownBy { service().markAsRead(ownerMemberId, 1L) }
            .hasMessage("본인의 알림만 접근할 수 있습니다.")
            .hasMessageNotContaining(otherMemberId.toString())
    }

    // ---------- 전체 읽음 ----------

    @Test
    fun `읽지 않은 알림을 한 번의 UPDATE로 모두 읽음 처리한다`() {
        given(notificationRepository.markAllAsRead(anyLong(), anyDateTime())).willReturn(3)

        val response = service().markAllAsRead(ownerMemberId)

        assertThat(response.updatedCount).isEqualTo(3L)
        assertThat(response.readAt).isNotNull
        verify(notificationRepository).markAllAsRead(ownerMemberId, response.readAt)
    }

    @Test
    fun `이미 모두 읽은 상태에서도 오류 없이 0건으로 응답한다`() {
        given(notificationRepository.markAllAsRead(anyLong(), anyDateTime())).willReturn(0)

        assertThat(service().markAllAsRead(ownerMemberId).updatedCount).isZero
    }

    @Test
    fun `전체 읽음 처리는 대상 해석을 하지 않는다`() {
        given(notificationRepository.markAllAsRead(anyLong(), anyDateTime())).willReturn(1)

        service().markAllAsRead(ownerMemberId)

        verifyNoInteractions(notificationTargetResolver)
    }

    private fun givenPage(content: List<Notification>) {
        given(
            notificationRepository.findMyNotifications(
                memberId = anyLong(),
                isRead = any(),
                type = any(),
                pageable = anyPageable(),
            ),
        ).willReturn(PageImpl(content, PageRequest.of(0, 20), content.size.toLong()))
    }
}
