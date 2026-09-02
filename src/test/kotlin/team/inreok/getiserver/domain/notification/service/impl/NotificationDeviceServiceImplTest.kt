package team.inreok.getiserver.domain.notification.service.impl

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import team.inreok.getiserver.domain.notification.dto.NotificationDeviceRegisterRequest
import team.inreok.getiserver.domain.notification.entity.NotificationDevice
import team.inreok.getiserver.domain.notification.entity.type.PushPlatform
import team.inreok.getiserver.domain.notification.exception.NotificationDeviceAccessDeniedException
import team.inreok.getiserver.domain.notification.exception.NotificationDeviceNotFoundException
import team.inreok.getiserver.domain.notification.repository.NotificationDeviceRepository
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationDeviceServiceImplTest {
    @Mock
    private lateinit var notificationDeviceRepository: NotificationDeviceRepository

    private val ownerMemberId = 1L
    private val otherMemberId = 2L
    private val deviceKey = "device-key-1"

    private fun service() = NotificationDeviceServiceImpl(notificationDeviceRepository)

    private fun device(
        memberId: Long = ownerMemberId,
        deviceKey: String = this.deviceKey,
        pushToken: String = "push-token",
        platform: PushPlatform = PushPlatform.ANDROID,
    ) = NotificationDevice(
        memberId = memberId,
        deviceKey = deviceKey,
        pushToken = pushToken,
        platform = platform,
    ).apply {
        id = 1L
        createdAt = LocalDateTime.of(2026, 8, 20, 10, 0)
        updatedAt = LocalDateTime.of(2026, 8, 20, 10, 0)
    }

    // ---------- 등록/갱신 ----------

    @Test
    fun `기기를 처음 등록하면 Upsert 후 저장된 값을 반환한다`() {
        given(notificationDeviceRepository.findByDeviceKey(deviceKey)).willReturn(device())

        val response =
            service().register(
                ownerMemberId,
                NotificationDeviceRegisterRequest(deviceKey, "push-token", PushPlatform.ANDROID),
            )

        verify(notificationDeviceRepository).upsert(ownerMemberId, deviceKey, "push-token", "ANDROID")
        assertThat(response.deviceKey).isEqualTo(deviceKey)
        assertThat(response.platform).isEqualTo(PushPlatform.ANDROID)
        assertThat(response.updatedAt).isNotNull()
    }

    @Test
    fun `같은 deviceKey로 다시 등록하면 갱신된 값을 반환한다`() {
        val updated =
            device(pushToken = "new-token", platform = PushPlatform.IOS).apply {
                updatedAt = LocalDateTime.of(2026, 8, 21, 9, 0)
            }
        given(notificationDeviceRepository.findByDeviceKey(deviceKey)).willReturn(updated)

        val response =
            service().register(
                ownerMemberId,
                NotificationDeviceRegisterRequest(deviceKey, "new-token", PushPlatform.IOS),
            )

        assertThat(response.platform).isEqualTo(PushPlatform.IOS)
        assertThat(response.updatedAt).isEqualTo(LocalDateTime.of(2026, 8, 21, 9, 0))
    }

    @Test
    fun `다른 회원이 같은 deviceKey로 등록하면 그 회원 소유로 이전된다`() {
        // Upsert 자체가 Row의 memberId를 새 값으로 덮어쓰므로(NotificationDeviceRepository.upsert
        // KDoc), Service는 Upsert 호출 뒤 그대로 조회 결과를 반환하기만 하면 된다. 여기서는 Upsert가
        // 실제로 새 소유자 기준으로 호출되는지만 검증한다(원자성 자체는 Persistence Integration
        // Test가 검증).
        given(notificationDeviceRepository.findByDeviceKey(deviceKey))
            .willReturn(device(memberId = otherMemberId))

        service().register(
            otherMemberId,
            NotificationDeviceRegisterRequest(deviceKey, "push-token", PushPlatform.ANDROID),
        )

        verify(notificationDeviceRepository).upsert(otherMemberId, deviceKey, "push-token", "ANDROID")
    }

    // ---------- 해제 ----------

    @Test
    fun `본인이 등록한 기기를 해제하면 삭제된다`() {
        val existing = device()
        given(notificationDeviceRepository.findByDeviceKey(deviceKey)).willReturn(existing)

        service().unregister(ownerMemberId, deviceKey)

        verify(notificationDeviceRepository).delete(existing)
    }

    @Test
    fun `다른 회원이 등록한 기기를 해제하려 하면 403 예외가 발생한다`() {
        given(notificationDeviceRepository.findByDeviceKey(deviceKey)).willReturn(device(memberId = otherMemberId))

        assertThatThrownBy { service().unregister(ownerMemberId, deviceKey) }
            .isInstanceOf(NotificationDeviceAccessDeniedException::class.java)
        verify(notificationDeviceRepository, never()).delete(any(NotificationDevice::class.java) ?: device())
    }

    @Test
    fun `없는 deviceKey를 해제하려 하면 404 예외가 발생한다`() {
        given(notificationDeviceRepository.findByDeviceKey(deviceKey)).willReturn(null)

        assertThatThrownBy { service().unregister(ownerMemberId, deviceKey) }
            .isInstanceOf(NotificationDeviceNotFoundException::class.java)
    }
}
