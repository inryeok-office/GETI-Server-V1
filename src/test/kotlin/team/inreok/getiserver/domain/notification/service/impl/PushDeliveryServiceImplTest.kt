package team.inreok.getiserver.domain.notification.service.impl

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import org.springframework.data.domain.Pageable
import team.inreok.getiserver.domain.notification.config.PushDeliveryProperties
import team.inreok.getiserver.domain.notification.dto.NotificationSettingResponse
import team.inreok.getiserver.domain.notification.entity.Notification
import team.inreok.getiserver.domain.notification.entity.NotificationDevice
import team.inreok.getiserver.domain.notification.entity.PushDelivery
import team.inreok.getiserver.domain.notification.entity.type.NotificationType
import team.inreok.getiserver.domain.notification.entity.type.PushDeliveryStatus
import team.inreok.getiserver.domain.notification.entity.type.PushPlatform
import team.inreok.getiserver.domain.notification.repository.NotificationDeviceRepository
import team.inreok.getiserver.domain.notification.repository.NotificationRepository
import team.inreok.getiserver.domain.notification.repository.PushDeliveryRepository
import team.inreok.getiserver.domain.notification.service.FakePushProvider
import team.inreok.getiserver.domain.notification.service.NotificationSettingService
import team.inreok.getiserver.domain.notification.service.PushDeliveryRetryPolicy
import team.inreok.getiserver.domain.notification.service.PushProvider
import team.inreok.getiserver.domain.notification.service.PushSendCommand
import team.inreok.getiserver.domain.notification.service.PushSendResult
import java.time.LocalDateTime
import java.util.Optional

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PushDeliveryServiceImplTest {
    @Mock
    private lateinit var pushDeliveryRepository: PushDeliveryRepository

    @Mock
    private lateinit var notificationRepository: NotificationRepository

    @Mock
    private lateinit var notificationDeviceRepository: NotificationDeviceRepository

    @Mock
    private lateinit var notificationSettingService: NotificationSettingService

    private val pushProvider = FakePushProvider()
    private val properties = PushDeliveryProperties()

    private fun service(provider: PushProvider = pushProvider) =
        PushDeliveryServiceImpl(
            pushDeliveryRepository = pushDeliveryRepository,
            notificationRepository = notificationRepository,
            notificationDeviceRepository = notificationDeviceRepository,
            notificationSettingService = notificationSettingService,
            pushProvider = provider,
            retryPolicy = PushDeliveryRetryPolicy(properties),
            properties = properties,
        )

    // --- 예약(enqueue) -----------------------------------------------------

    @Test
    fun `pushEnabled가 false면 예약하지 않는다`() {
        given(notificationSettingService.getSettings(1L)).willReturn(NotificationSettingResponse(pushEnabled = false))

        service().enqueueForNotification(10L, 1L, NotificationType.JOB_PUBLISHED)

        Mockito.verifyNoInteractions(notificationDeviceRepository)
        Mockito.verify(pushDeliveryRepository, Mockito.never()).save(anyDelivery())
    }

    @Test
    fun `pushEnabled가 true여도 등록된 기기가 없으면 예약하지 않는다`() {
        given(notificationSettingService.getSettings(1L)).willReturn(NotificationSettingResponse(pushEnabled = true))
        given(notificationDeviceRepository.findIdsByMemberId(1L)).willReturn(emptyList())

        service().enqueueForNotification(10L, 1L, NotificationType.JOB_PUBLISHED)

        Mockito.verify(pushDeliveryRepository, Mockito.never()).save(anyDelivery())
    }

    @Test
    fun `기기 1대면 PushDelivery를 1건 예약한다`() {
        given(notificationSettingService.getSettings(1L)).willReturn(NotificationSettingResponse(pushEnabled = true))
        given(notificationDeviceRepository.findIdsByMemberId(1L)).willReturn(listOf(100L))

        service().enqueueForNotification(10L, 1L, NotificationType.JOB_PUBLISHED)

        val captor = ArgumentCaptor.forClass(PushDelivery::class.java)
        Mockito.verify(pushDeliveryRepository).save(captor.capture())
        with(captor.value) {
            assertThat(notificationId).isEqualTo(10L)
            assertThat(memberId).isEqualTo(1L)
            assertThat(deviceId).isEqualTo(100L)
            assertThat(status).isEqualTo(PushDeliveryStatus.PENDING)
        }
    }

    @Test
    fun `기기가 여러 대면 기기 수만큼 예약한다`() {
        given(notificationSettingService.getSettings(1L)).willReturn(NotificationSettingResponse(pushEnabled = true))
        given(notificationDeviceRepository.findIdsByMemberId(1L)).willReturn(listOf(100L, 101L, 102L))

        service().enqueueForNotification(10L, 1L, NotificationType.JOB_PUBLISHED)

        Mockito.verify(pushDeliveryRepository, Mockito.times(3)).save(anyDelivery())
    }

    @Test
    fun `Push 대상 Type이면 예약한다`() {
        given(notificationSettingService.getSettings(1L)).willReturn(NotificationSettingResponse(pushEnabled = true))
        given(notificationDeviceRepository.findIdsByMemberId(1L)).willReturn(listOf(100L))

        service().enqueueForNotification(10L, 1L, NotificationType.INQUIRY_ANSWERED)

        Mockito.verify(pushDeliveryRepository).save(anyDelivery())
    }

    @Test
    fun `Push 제외 Type이면 회원·기기 조회 없이 예약하지 않는다`() {
        service().enqueueForNotification(10L, 1L, NotificationType.JOB_UPDATED)

        Mockito.verifyNoInteractions(notificationSettingService)
        Mockito.verifyNoInteractions(notificationDeviceRepository)
        Mockito.verify(pushDeliveryRepository, Mockito.never()).save(anyDelivery())
    }

    // --- 전송 성공/실패 ------------------------------------------------------

    @Test
    fun `전송에 성공하면 SENT가 된다`() {
        val delivery = claimable(pushDelivery(id = 1L, deviceId = 100L))
        pushProvider.willReturn(PushSendResult.Success("msg-1"))

        service().processDueDeliveries()

        assertThat(delivery.status).isEqualTo(PushDeliveryStatus.SENT)
        assertThat(delivery.sentAt).isNotNull()
        assertThat(delivery.nextRetryAt).isNull()
    }

    @Test
    fun `기기 1대에 발송하면 Provider가 1번 호출된다`() {
        claimable(pushDelivery(id = 1L, deviceId = 100L))
        pushProvider.willReturn(PushSendResult.Success("msg-1"))

        service().processDueDeliveries()

        assertThat(pushProvider.callCount()).isEqualTo(1)
    }

    @Test
    fun `재시도 가능한 일시적 실패는 PENDING으로 남고 다음 시도 시각이 예약된다`() {
        val delivery = claimable(pushDelivery(id = 1L, deviceId = 100L))
        pushProvider.willReturn(
            PushSendResult.Failure("UNAVAILABLE", retryable = true, invalidToken = false, message = "일시 오류"),
        )

        service().processDueDeliveries()

        assertThat(delivery.status).isEqualTo(PushDeliveryStatus.PENDING)
        assertThat(delivery.retryCount).isEqualTo(1)
        assertThat(delivery.nextRetryAt).isNotNull()
        assertThat(delivery.lastErrorCode).isEqualTo("UNAVAILABLE")
    }

    @Test
    fun `재시도 횟수가 상한(기본 3회)에 도달하면 FAILED가 된다`() {
        val delivery = claimable(pushDelivery(id = 1L, deviceId = 100L, retryCount = 3))
        pushProvider.willReturn(
            PushSendResult.Failure("UNAVAILABLE", retryable = true, invalidToken = false, message = null),
        )

        service().processDueDeliveries()

        assertThat(delivery.status).isEqualTo(PushDeliveryStatus.FAILED)
        assertThat(delivery.nextRetryAt).isNull()
        // 상한을 넘는 값이 저장되지 않는다(DiscordDelivery.markFailed와 같은 이유).
        assertThat(delivery.retryCount).isEqualTo(3)
    }

    @Test
    fun `재시도 불가능한 실패는 즉시 FAILED가 된다`() {
        val delivery = claimable(pushDelivery(id = 1L, deviceId = 100L))
        pushProvider.willReturn(
            PushSendResult.Failure("INVALID_ARGUMENT", retryable = false, invalidToken = false, message = null),
        )

        service().processDueDeliveries()

        assertThat(delivery.status).isEqualTo(PushDeliveryStatus.FAILED)
        assertThat(delivery.nextRetryAt).isNull()
    }

    @Test
    fun `무효 Token 실패는 재시도하지 않고 기기 등록을 제거한다`() {
        val delivery = claimable(pushDelivery(id = 1L, deviceId = 100L))
        given(notificationDeviceRepository.existsById(100L)).willReturn(true)
        pushProvider.willReturn(
            PushSendResult.Failure("UNREGISTERED", retryable = false, invalidToken = true, message = "만료된 Token"),
        )

        service().processDueDeliveries()

        assertThat(delivery.status).isEqualTo(PushDeliveryStatus.FAILED)
        assertThat(delivery.nextRetryAt).isNull()
        Mockito.verify(notificationDeviceRepository).deleteById(100L)
    }

    @Test
    fun `무효 Token 기기가 이미 삭제됐으면 다시 지우지 않는다`() {
        claimable(pushDelivery(id = 1L, deviceId = 100L))
        given(notificationDeviceRepository.existsById(100L)).willReturn(false)
        pushProvider.willReturn(
            PushSendResult.Failure("UNREGISTERED", retryable = false, invalidToken = true, message = null),
        )

        service().processDueDeliveries()

        Mockito.verify(notificationDeviceRepository, Mockito.never()).deleteById(anyLong())
    }

    @Test
    fun `한 기기의 전송 실패가 다른 기기 전송에 영향을 주지 않는다`() {
        val failing = pushDelivery(id = 1L, deviceId = 100L)
        val succeeding = pushDelivery(id = 2L, deviceId = 200L)
        given(pushDeliveryRepository.findDueIds(anyStatus(), anyDateTime(), anyPageable())).willReturn(listOf(1L, 2L))
        given(pushDeliveryRepository.claim(anyLong(), anyStatus(), anyStatus(), anyDateTime())).willReturn(1)
        given(pushDeliveryRepository.findById(1L)).willReturn(Optional.of(failing))
        given(pushDeliveryRepository.findById(2L)).willReturn(Optional.of(succeeding))
        given(notificationRepository.findById(failing.notificationId))
            .willReturn(Optional.of(notification(failing.notificationId)))
        given(notificationRepository.findById(succeeding.notificationId))
            .willReturn(Optional.of(notification(succeeding.notificationId)))
        given(notificationDeviceRepository.findById(100L)).willReturn(Optional.of(device(100L)))
        given(notificationDeviceRepository.findById(200L)).willReturn(Optional.of(device(200L)))

        var callCount = 0
        val flakyProvider =
            PushProvider { _: PushSendCommand ->
                callCount++
                if (callCount == 1) {
                    PushSendResult.Failure("UNAVAILABLE", retryable = true, invalidToken = false, message = null)
                } else {
                    PushSendResult.Success("msg-2")
                }
            }

        service(flakyProvider).processDueDeliveries()

        assertThat(failing.status).isEqualTo(PushDeliveryStatus.PENDING)
        assertThat(succeeding.status).isEqualTo(PushDeliveryStatus.SENT)
    }

    // --- 사전 검증 실패 ------------------------------------------------------

    @Test
    fun `알림을 찾을 수 없으면 Provider를 호출하지 않고 FAILED가 된다`() {
        val delivery = pushDelivery(id = 1L, deviceId = 100L)
        given(pushDeliveryRepository.findDueIds(anyStatus(), anyDateTime(), anyPageable())).willReturn(listOf(1L))
        given(pushDeliveryRepository.claim(anyLong(), anyStatus(), anyStatus(), anyDateTime())).willReturn(1)
        given(pushDeliveryRepository.findById(1L)).willReturn(Optional.of(delivery))
        given(notificationRepository.findById(delivery.notificationId)).willReturn(Optional.empty())

        service().processDueDeliveries()

        assertThat(delivery.status).isEqualTo(PushDeliveryStatus.FAILED)
        assertThat(delivery.lastErrorCode).isEqualTo("NOTIFICATION_UNAVAILABLE")
        assertThat(pushProvider.callCount()).isZero()
    }

    @Test
    fun `기기를 찾을 수 없으면 Provider를 호출하지 않고 FAILED가 된다`() {
        val delivery = pushDelivery(id = 1L, deviceId = 100L)
        given(pushDeliveryRepository.findDueIds(anyStatus(), anyDateTime(), anyPageable())).willReturn(listOf(1L))
        given(pushDeliveryRepository.claim(anyLong(), anyStatus(), anyStatus(), anyDateTime())).willReturn(1)
        given(pushDeliveryRepository.findById(1L)).willReturn(Optional.of(delivery))
        given(notificationRepository.findById(delivery.notificationId))
            .willReturn(Optional.of(notification(delivery.notificationId)))
        given(notificationDeviceRepository.findById(100L)).willReturn(Optional.empty())

        service().processDueDeliveries()

        assertThat(delivery.status).isEqualTo(PushDeliveryStatus.FAILED)
        assertThat(delivery.lastErrorCode).isEqualTo("DEVICE_NOT_FOUND")
        assertThat(pushProvider.callCount()).isZero()
    }

    // --- 선점 -----------------------------------------------------------

    @Test
    fun `다른 인스턴스가 이미 선점했으면 전송하지 않는다`() {
        val delivery = pushDelivery(id = 1L, deviceId = 100L)
        given(pushDeliveryRepository.findDueIds(anyStatus(), anyDateTime(), anyPageable())).willReturn(listOf(1L))
        given(pushDeliveryRepository.claim(anyLong(), anyStatus(), anyStatus(), anyDateTime())).willReturn(0)
        given(pushDeliveryRepository.findById(1L)).willReturn(Optional.of(delivery))

        assertThat(service().processDueDeliveries()).isZero()
        assertThat(pushProvider.callCount()).isZero()
    }

    @Test
    fun `Sweep은 Stale PROCESSING을 먼저 회수한다`() {
        given(pushDeliveryRepository.findDueIds(anyStatus(), anyDateTime(), anyPageable())).willReturn(emptyList())

        service().processDueDeliveries()

        Mockito
            .verify(pushDeliveryRepository)
            .recoverStaleProcessing(anyStatus(), anyStatus(), anyDateTime(), anyDateTime())
    }

    // --- Matcher Helper ------------------------------------------------------

    // Kotlin non-null 파라미터에 bare any()를 쓰면 null 반환으로 NPE가 나므로 Elvis 기본값을
    // 채운다(DiscordDeliveryServiceImplTest와 같은 이유).
    private fun anyDelivery(): PushDelivery = any(PushDelivery::class.java) ?: pushDelivery(id = 0L, deviceId = 0L)

    private fun anyStatus(): PushDeliveryStatus = any(PushDeliveryStatus::class.java) ?: PushDeliveryStatus.PENDING

    private fun anyDateTime(): LocalDateTime = any(LocalDateTime::class.java) ?: LocalDateTime.now()

    private fun anyPageable(): Pageable = any(Pageable::class.java) ?: Pageable.unpaged()

    // --- Fixture ---------------------------------------------------------

    private fun pushDelivery(
        id: Long,
        deviceId: Long,
        notificationId: Long = 10L,
        memberId: Long = 1L,
        retryCount: Int = 0,
    ) = PushDelivery(
        notificationId = notificationId,
        memberId = memberId,
        deviceId = deviceId,
    ).apply {
        this.id = id
        this.retryCount = retryCount
    }

    private fun notification(id: Long) =
        Notification(
            recipientMemberId = 1L,
            type = NotificationType.JOB_PUBLISHED,
            title = "새 공고",
            content = "새 공고가 등록되었습니다.",
        ).apply { this.id = id }

    private fun device(id: Long) =
        NotificationDevice(
            memberId = 1L,
            deviceKey = "device-$id",
            pushToken = "token-$id",
            platform = PushPlatform.ANDROID,
        ).apply { this.id = id }

    /** Sweep이 이 Delivery를 실제로 집어 처리하도록 Repository Mock을 준비한다. */
    private fun claimable(delivery: PushDelivery): PushDelivery {
        val id = requireNotNull(delivery.id)
        given(pushDeliveryRepository.findDueIds(anyStatus(), anyDateTime(), anyPageable())).willReturn(listOf(id))
        given(pushDeliveryRepository.claim(anyLong(), anyStatus(), anyStatus(), anyDateTime())).willReturn(1)
        given(pushDeliveryRepository.findById(id)).willReturn(Optional.of(delivery))
        given(notificationRepository.findById(delivery.notificationId))
            .willReturn(Optional.of(notification(delivery.notificationId)))
        given(notificationDeviceRepository.findById(delivery.deviceId))
            .willReturn(Optional.of(device(delivery.deviceId)))
        return delivery
    }
}
