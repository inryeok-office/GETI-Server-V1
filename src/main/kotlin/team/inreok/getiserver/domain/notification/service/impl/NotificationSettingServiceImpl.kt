package team.inreok.getiserver.domain.notification.service.impl

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.notification.dto.NotificationSettingResponse
import team.inreok.getiserver.domain.notification.repository.NotificationSettingRepository
import team.inreok.getiserver.domain.notification.service.NotificationSettingService

@Service
class NotificationSettingServiceImpl(
    private val notificationSettingRepository: NotificationSettingRepository,
) : NotificationSettingService {
    @Transactional(readOnly = true)
    override fun getSettings(memberId: Long): NotificationSettingResponse =
        NotificationSettingResponse(
            pushEnabled =
                notificationSettingRepository
                    .findById(memberId)
                    .map { it.pushEnabled }
                    .orElse(DEFAULT_PUSH_ENABLED),
        )

    @Transactional
    override fun updateSettings(
        memberId: Long,
        pushEnabled: Boolean,
    ): NotificationSettingResponse {
        // find-then-save 2단계 대신 Upsert 하나로 처리해 동시 최초 설정 요청에서도 멱등을
        // 보장한다(RecommendationServiceImpl.updateSetting과 같은 이유).
        notificationSettingRepository.upsert(memberId, pushEnabled)
        return NotificationSettingResponse(pushEnabled = pushEnabled)
    }

    companion object {
        // 설정한 적 없는 회원의 기본값이다. Issue #189/Notion 어디에도 기본값이 명시돼 있지 않아
        // Push 알림을 기본 수신(opt-out)으로 판단했다(DECISION_REQUIRED, PR 본문에 가정을
        // 명시한다) -- 기기 등록 자체가 OS 수준 Push 권한 동의를 전제로 하므로, 서버 설정까지
        // 기본 차단하면 등록 직후에도 발송이 막히는 것이 부자연스럽다고 판단했다.
        private const val DEFAULT_PUSH_ENABLED = true
    }
}
