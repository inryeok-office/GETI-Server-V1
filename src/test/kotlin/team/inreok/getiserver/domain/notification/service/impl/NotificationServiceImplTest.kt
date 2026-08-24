package team.inreok.getiserver.domain.notification.service.impl

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.hibernate.exception.ConstraintViolationException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.isNull
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import team.inreok.getiserver.domain.notification.dto.NotificationCreateCommand
import team.inreok.getiserver.domain.notification.dto.NotificationReadRequest
import team.inreok.getiserver.domain.notification.dto.NotificationReadScope
import team.inreok.getiserver.domain.notification.entity.Notification
import team.inreok.getiserver.domain.notification.entity.type.NotificationTargetType
import team.inreok.getiserver.domain.notification.entity.type.NotificationTargetUnavailableReason
import team.inreok.getiserver.domain.notification.entity.type.NotificationType
import team.inreok.getiserver.domain.notification.exception.NotificationAccessDeniedException
import team.inreok.getiserver.domain.notification.exception.NotificationIdRequiredException
import team.inreok.getiserver.domain.notification.exception.NotificationNotFoundException
import team.inreok.getiserver.domain.notification.repository.NotificationRepository
import team.inreok.getiserver.domain.notification.service.NotificationTargetAvailability
import team.inreok.getiserver.domain.notification.service.NotificationTargetRef
import team.inreok.getiserver.domain.notification.service.NotificationTargetResolver
import team.inreok.getiserver.domain.notification.service.PushDeliveryService
import java.sql.SQLException
import java.time.LocalDateTime
import java.util.Optional

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationServiceImplTest {
    @Mock
    private lateinit var notificationRepository: NotificationRepository

    @Mock
    private lateinit var notificationTargetResolver: NotificationTargetResolver

    @Mock
    private lateinit var pushDeliveryService: PushDeliveryService

    private val ownerMemberId = 1L
    private val otherMemberId = 2L

    private fun service() =
        NotificationServiceImpl(
            notificationRepository,
            notificationTargetResolver,
            NotificationInsertOperation(notificationRepository),
            pushDeliveryService,
        )

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

    private fun anyTargetRefs(): Set<NotificationTargetRef> = any() ?: emptySet()

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
                    sourceEventType = "ProgramPublishedEvent",
                    sourceEventId = 1L,
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
                sourceEventType = "SystemNoticeEvent",
                sourceEventId = 1L,
            ),
        )

        assertThat(captor.value.targetType).isNull()
        assertThat(captor.value.targetId).isNull()
    }

    @Test
    fun `같은 Idempotency Identity로 UNIQUE 제약을 위반하면 예외 대신 기존 알림 id를 반환한다`() {
        val command =
            NotificationCreateCommand(
                recipientMemberId = ownerMemberId,
                type = NotificationType.INQUIRY_ANSWERED,
                title = "문의 답변",
                content = "답변이 등록되었습니다.",
                sourceEventType = "InquiryAnsweredEvent",
                sourceEventId = 55L,
            )
        val duplicateViolation =
            DataIntegrityViolationException(
                "duplicate",
                ConstraintViolationException("duplicate", SQLException(), "uk_notifications_recipient_source_event"),
            )
        given(notificationRepository.save(any() ?: notification(1L)))
            .willThrow(duplicateViolation)
        given(
            notificationRepository.findByRecipientMemberIdAndSourceEventTypeAndSourceEventId(
                ownerMemberId,
                "InquiryAnsweredEvent",
                55L,
            ),
        ).willReturn(notification(9L))

        val createdId = service().create(command)

        assertThat(createdId).isEqualTo(9L)
    }

    @Test
    fun `새 알림을 만들면 Push 전달을 예약한다`() {
        given(notificationRepository.save(any() ?: notification(1L)))
            .willAnswer { invocation -> invocation.getArgument<Notification>(0).apply { id = 7L } }

        service().create(
            NotificationCreateCommand(
                recipientMemberId = ownerMemberId,
                type = NotificationType.JOB_PUBLISHED,
                title = "새 공고",
                content = "새 공고가 등록되었습니다.",
                sourceEventType = "JobPublishedEvent",
                sourceEventId = 1L,
            ),
        )

        verify(pushDeliveryService).enqueueForNotification(7L, ownerMemberId, NotificationType.JOB_PUBLISHED)
    }

    @Test
    fun `Idempotency로 기존 알림을 재사용하면 Push를 다시 예약하지 않는다`() {
        val command =
            NotificationCreateCommand(
                recipientMemberId = ownerMemberId,
                type = NotificationType.INQUIRY_ANSWERED,
                title = "문의 답변",
                content = "답변이 등록되었습니다.",
                sourceEventType = "InquiryAnsweredEvent",
                sourceEventId = 55L,
            )
        val duplicateViolation =
            DataIntegrityViolationException(
                "duplicate",
                ConstraintViolationException("duplicate", SQLException(), "uk_notifications_recipient_source_event"),
            )
        given(notificationRepository.save(any() ?: notification(1L))).willThrow(duplicateViolation)
        given(
            notificationRepository.findByRecipientMemberIdAndSourceEventTypeAndSourceEventId(
                ownerMemberId,
                "InquiryAnsweredEvent",
                55L,
            ),
        ).willReturn(notification(9L))

        service().create(command)

        verify(pushDeliveryService, never()).enqueueForNotification(
            anyLong(),
            anyLong(),
            any() ?: NotificationType.SYSTEM,
        )
    }

    @Test
    fun `Push 예약 중 오류가 나도 알림 생성은 그대로 성공한다`() {
        // Push는 알림 생성의 부가 기능이다(Issue #190) -- Push 실패가 원본 알림 생성을 실패로
        // 만들면 안 된다(Business Transaction과 독립).
        given(notificationRepository.save(any() ?: notification(1L)))
            .willAnswer { invocation -> invocation.getArgument<Notification>(0).apply { id = 7L } }
        org.mockito.BDDMockito
            .willThrow(RuntimeException("FCM 설정 오류"))
            .given(pushDeliveryService)
            .enqueueForNotification(anyLong(), anyLong(), any() ?: NotificationType.SYSTEM)

        val createdId =
            service().create(
                NotificationCreateCommand(
                    recipientMemberId = ownerMemberId,
                    type = NotificationType.JOB_PUBLISHED,
                    title = "새 공고",
                    content = "새 공고가 등록되었습니다.",
                    sourceEventType = "JobPublishedEvent",
                    sourceEventId = 1L,
                ),
            )

        assertThat(createdId).isEqualTo(7L)
    }

    @Test
    fun `UNIQUE 제약과 무관한 저장 실패는 그대로 다시 던진다`() {
        val command =
            NotificationCreateCommand(
                recipientMemberId = ownerMemberId,
                type = NotificationType.INQUIRY_ANSWERED,
                title = "문의 답변",
                content = "답변이 등록되었습니다.",
                sourceEventType = "InquiryAnsweredEvent",
                sourceEventId = 55L,
            )
        val unrelatedViolation = DataIntegrityViolationException("column too long")
        given(notificationRepository.save(any() ?: notification(1L)))
            .willThrow(unrelatedViolation)

        assertThatThrownBy { service().create(command) }.isSameAs(unrelatedViolation)
        verify(notificationRepository, never())
            .findByRecipientMemberIdAndSourceEventTypeAndSourceEventId(anyLong(), anyString(), anyLong())
    }

    // ---------- 목록 ----------

    @Test
    fun `목록은 대상 접근 가능 여부와 deepLink를 계산해서 내려준다`() {
        val target = NotificationTargetRef(NotificationTargetType.PROGRAM, 100L)
        givenPage(listOf(notification(1L)))
        given(notificationTargetResolver.resolveAll(setOf(target), ownerMemberId))
            .willReturn(mapOf(target to availableTarget))

        val response = service().list(ownerMemberId, false, null, PageRequest.of(0, 20))

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

        val response = service().list(ownerMemberId, false, null, PageRequest.of(0, 20))

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

        val response = service().list(ownerMemberId, false, null, PageRequest.of(0, 20))

        with(response.content.first()) {
            assertThat(targetType).isNull()
            assertThat(targetId).isNull()
            assertThat(targetAvailable).isFalse
            assertThat(targetUnavailableReason).isNull()
            assertThat(deepLink).isNull()
        }
    }

    @Test
    fun `unreadOnly가 true면 읽지 않은 알림만 조회하고 종류 필터도 그대로 전달한다`() {
        givenPage(emptyList())
        given(notificationTargetResolver.resolveAll(emptySet(), ownerMemberId)).willReturn(emptyMap())

        service().list(ownerMemberId, true, NotificationType.JOB_PUBLISHED, PageRequest.of(1, 50))

        verify(notificationRepository).findMyNotifications(
            memberId = ownerMemberId,
            isRead = false,
            type = NotificationType.JOB_PUBLISHED,
            pageable = PageRequest.of(1, 50),
        )
    }

    @Test
    fun `unreadOnly가 false면 읽음 여부 조건을 걸지 않는다`() {
        givenPage(emptyList())
        given(notificationTargetResolver.resolveAll(emptySet(), ownerMemberId)).willReturn(emptyMap())

        // false는 "읽은 것만"이 아니라 "필터 없음"이다(Notion 계약이 Boolean Flag이기 때문).
        service().list(ownerMemberId, false, null, PageRequest.of(0, 20))

        verify(notificationRepository).findMyNotifications(
            memberId = ownerMemberId,
            isRead = null,
            type = null,
            pageable = PageRequest.of(0, 20),
        )
    }

    @Test
    fun `목록의 unreadCount는 필터와 무관한 전체 미읽음 수다`() {
        givenPage(listOf(notification(1L, isRead = true, readAt = LocalDateTime.of(2026, 8, 1, 9, 0))))
        given(notificationTargetResolver.resolveAll(anyTargetRefs(), anyLong())).willReturn(emptyMap())
        given(notificationRepository.countByRecipientMemberIdAndIsReadFalseAndDeletedAtIsNull(ownerMemberId))
            .willReturn(7L)

        // 읽은 알림만 담긴 Page를 받아도 Badge용 전체 미읽음 수는 그대로 내려간다.
        val response = service().list(ownerMemberId, true, NotificationType.JOB_PUBLISHED, PageRequest.of(0, 20))

        assertThat(response.unreadCount).isEqualTo(7L)
    }

    @Test
    fun `클라이언트가 보낸 정렬은 무시한다`() {
        givenPage(emptyList())
        given(notificationTargetResolver.resolveAll(emptySet(), ownerMemberId)).willReturn(emptyMap())

        service().list(ownerMemberId, false, null, PageRequest.of(0, 20, Sort.by("title").ascending()))

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
        given(notificationRepository.countByRecipientMemberIdAndIsReadFalseAndDeletedAtIsNull(ownerMemberId))
            .willReturn(2L)

        val response = service().read(ownerMemberId, single(1L))

        assertThat(response.updatedCount).isEqualTo(1L)
        assertThat(response.unreadCount).isEqualTo(2L)
        assertThat(response.readAt).isNotNull
        assertThat(target.isRead).isTrue
        assertThat(target.readAt).isNotNull
    }

    @Test
    fun `이미 읽은 알림을 다시 읽음 처리하면 갱신 0건이고 처음 읽은 시각이 유지된다`() {
        val firstReadAt = LocalDateTime.of(2026, 8, 1, 9, 0)
        val target = notification(1L, isRead = true, readAt = firstReadAt)
        given(notificationRepository.findById(1L)).willReturn(Optional.of(target))

        val response = service().read(ownerMemberId, single(1L))

        assertThat(response.updatedCount).isZero
        assertThat(response.readAt).isEqualTo(firstReadAt)
        assertThat(target.readAt).isEqualTo(firstReadAt)
    }

    @Test
    fun `scope가 SINGLE인데 notificationId가 없으면 NOTIFICATION_ID_REQUIRED다`() {
        assertThatThrownBy {
            service().read(ownerMemberId, NotificationReadRequest(NotificationReadScope.SINGLE, null))
        }.isInstanceOf(NotificationIdRequiredException::class.java)

        verifyNoInteractions(notificationRepository)
    }

    @Test
    fun `없는 알림을 읽음 처리하면 NOTIFICATION_NOT_FOUND다`() {
        given(notificationRepository.findById(99L)).willReturn(Optional.empty())

        assertThatThrownBy { service().read(ownerMemberId, single(99L)) }
            .isInstanceOf(NotificationNotFoundException::class.java)
    }

    @Test
    fun `삭제된 알림을 읽음 처리하면 NOTIFICATION_NOT_FOUND다`() {
        val target = notification(1L, deletedAt = LocalDateTime.of(2026, 8, 5, 9, 0))
        given(notificationRepository.findById(1L)).willReturn(Optional.of(target))

        assertThatThrownBy { service().read(ownerMemberId, single(1L)) }
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

        assertThatThrownBy { service().read(ownerMemberId, single(1L)) }
            .isInstanceOf(NotificationNotFoundException::class.java)
    }

    @Test
    fun `다른 사용자의 알림을 읽음 처리하면 NOTIFICATION_ACCESS_DENIED다`() {
        val target = notification(1L, recipientMemberId = otherMemberId)
        given(notificationRepository.findById(1L)).willReturn(Optional.of(target))

        assertThatThrownBy { service().read(ownerMemberId, single(1L)) }
            .isInstanceOf(NotificationAccessDeniedException::class.java)

        // 남의 알림 상태를 바꾸지 않는다.
        assertThat(target.isRead).isFalse
        assertThat(target.readAt).isNull()
    }

    @Test
    fun `다른 사용자의 알림이라는 사실을 예외 Message로 알려주지 않는다`() {
        val target = notification(1L, recipientMemberId = otherMemberId)
        given(notificationRepository.findById(1L)).willReturn(Optional.of(target))

        assertThatThrownBy { service().read(ownerMemberId, single(1L)) }
            .hasMessage("본인의 알림만 접근할 수 있습니다.")
            .hasMessageNotContaining(otherMemberId.toString())
    }

    // ---------- 전체 읽음 ----------

    @Test
    fun `읽지 않은 알림을 한 번의 UPDATE로 모두 읽음 처리한다`() {
        given(notificationRepository.markAllAsRead(anyLong(), anyDateTime())).willReturn(3)

        val response = service().read(ownerMemberId, NotificationReadRequest(NotificationReadScope.ALL))

        assertThat(response.updatedCount).isEqualTo(3L)
        assertThat(response.readAt).isNotNull
        verify(notificationRepository).markAllAsRead(ownerMemberId, requireNotNull(response.readAt))
    }

    @Test
    fun `이미 모두 읽은 상태에서도 오류 없이 0건으로 응답하고 readAt은 내려주지 않는다`() {
        given(notificationRepository.markAllAsRead(anyLong(), anyDateTime())).willReturn(0)

        val response = service().read(ownerMemberId, NotificationReadRequest(NotificationReadScope.ALL))

        assertThat(response.updatedCount).isZero
        assertThat(response.readAt).isNull()
    }

    @Test
    fun `scope가 ALL이면 notificationId를 함께 보내도 무시한다`() {
        given(notificationRepository.markAllAsRead(anyLong(), anyDateTime())).willReturn(2)

        val response = service().read(ownerMemberId, NotificationReadRequest(NotificationReadScope.ALL, 1L))

        assertThat(response.updatedCount).isEqualTo(2L)
        // 단건 조회 경로를 타지 않았다는 뜻이다.
        verify(notificationRepository, never()).findById(anyLong())
    }

    @Test
    fun `전체 읽음 처리는 대상 해석을 하지 않는다`() {
        given(notificationRepository.markAllAsRead(anyLong(), anyDateTime())).willReturn(1)

        service().read(ownerMemberId, NotificationReadRequest(NotificationReadScope.ALL))

        verifyNoInteractions(notificationTargetResolver)
    }

    // ---------- 삭제 ----------

    @Test
    fun `본인 알림을 삭제하면 deletedAt이 채워진다`() {
        val target = notification(1L)
        given(notificationRepository.findById(1L)).willReturn(Optional.of(target))

        service().delete(ownerMemberId, 1L)

        assertThat(target.deletedAt).isNotNull
        // 삭제는 읽음 여부를 건드리지 않는다(조회 Query가 deletedAt으로 이미 걸러낸다).
        assertThat(target.isRead).isFalse
    }

    @Test
    fun `다른 사용자의 알림은 삭제할 수 없다`() {
        val target = notification(1L, recipientMemberId = otherMemberId)
        given(notificationRepository.findById(1L)).willReturn(Optional.of(target))

        assertThatThrownBy { service().delete(ownerMemberId, 1L) }
            .isInstanceOf(NotificationAccessDeniedException::class.java)

        assertThat(target.deletedAt).isNull()
    }

    @Test
    fun `없는 알림을 삭제하면 NOTIFICATION_NOT_FOUND다`() {
        given(notificationRepository.findById(99L)).willReturn(Optional.empty())

        assertThatThrownBy { service().delete(ownerMemberId, 99L) }
            .isInstanceOf(NotificationNotFoundException::class.java)
    }

    @Test
    fun `이미 삭제한 알림을 다시 삭제하면 NOTIFICATION_NOT_FOUND다`() {
        val deletedAt = LocalDateTime.of(2026, 8, 5, 9, 0)
        val target = notification(1L, deletedAt = deletedAt)
        given(notificationRepository.findById(1L)).willReturn(Optional.of(target))

        assertThatThrownBy { service().delete(ownerMemberId, 1L) }
            .isInstanceOf(NotificationNotFoundException::class.java)

        // 삭제 시각을 덮어쓰지 않는다.
        assertThat(target.deletedAt).isEqualTo(deletedAt)
    }

    private fun single(notificationId: Long) = NotificationReadRequest(NotificationReadScope.SINGLE, notificationId)

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
