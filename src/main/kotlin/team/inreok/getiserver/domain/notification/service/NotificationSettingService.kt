package team.inreok.getiserver.domain.notification.service

import team.inreok.getiserver.domain.notification.dto.NotificationSettingResponse

interface NotificationSettingService {
    /** 요청자 본인의 푸시 알림 수신 여부를 조회한다. 설정한 적 없으면 기본값(true)을 반환한다. */
    fun getSettings(memberId: Long): NotificationSettingResponse

    /**
     * 요청자 본인의 푸시 알림 수신 여부를 변경한다. 이전 값과 같은 값을 다시 보내도 오류 없이
     * 성공한다(멱등). `false`로 꺼도 등록된 기기(Row)는 삭제되지 않고 인앱 알림 생성도 계속되며,
     * Push 발송만 멈춘다(발송 여부 판단은 Issue #190의 책임).
     */
    fun updateSettings(
        memberId: Long,
        pushEnabled: Boolean,
    ): NotificationSettingResponse
}
