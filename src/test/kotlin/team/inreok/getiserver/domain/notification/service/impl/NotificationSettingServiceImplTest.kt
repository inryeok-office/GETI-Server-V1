package team.inreok.getiserver.domain.notification.service.impl

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import team.inreok.getiserver.domain.notification.entity.NotificationSetting
import team.inreok.getiserver.domain.notification.repository.NotificationSettingRepository
import java.util.Optional

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationSettingServiceImplTest {
    @Mock
    private lateinit var notificationSettingRepository: NotificationSettingRepository

    private val memberId = 1L

    private fun service() = NotificationSettingServiceImpl(notificationSettingRepository)

    @Test
    fun `설정한 적 없는 회원은 기본값(true)을 반환한다`() {
        given(notificationSettingRepository.findById(memberId)).willReturn(Optional.empty())

        val response = service().getSettings(memberId)

        assertThat(response.pushEnabled).isTrue()
    }

    @Test
    fun `저장된 설정이 있으면 그 값을 반환한다`() {
        given(notificationSettingRepository.findById(memberId))
            .willReturn(Optional.of(NotificationSetting(memberId = memberId, pushEnabled = false)))

        val response = service().getSettings(memberId)

        assertThat(response.pushEnabled).isFalse()
    }

    @Test
    fun `설정을 변경하면 Upsert를 호출하고 변경된 값을 반환한다`() {
        val response = service().updateSettings(memberId, false)

        verify(notificationSettingRepository).upsert(memberId, false)
        assertThat(response.pushEnabled).isFalse()
    }

    @Test
    fun `이전 값과 같은 값을 다시 보내도 오류 없이 성공한다`() {
        val response = service().updateSettings(memberId, true)

        verify(notificationSettingRepository).upsert(memberId, true)
        assertThat(response.pushEnabled).isTrue()
    }
}
