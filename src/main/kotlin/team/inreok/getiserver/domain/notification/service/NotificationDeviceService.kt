package team.inreok.getiserver.domain.notification.service

import team.inreok.getiserver.domain.notification.dto.NotificationDeviceRegisterRequest
import team.inreok.getiserver.domain.notification.dto.NotificationDeviceResponse

interface NotificationDeviceService {
    /**
     * 요청자 본인 명의로 푸시 기기를 등록/갱신한다(Issue #189). 같은 `deviceKey`가 이미 있으면
     * Row를 갱신하고(재등록), 다른 회원이 등록한 `deviceKey`였으면 이 요청의 회원으로 소유권을
     * 이전한다(이전 소유자에게 별도 알림·거부 절차는 없다).
     */
    fun register(
        memberId: Long,
        request: NotificationDeviceRegisterRequest,
    ): NotificationDeviceResponse

    /**
     * 요청자 본인이 등록한 기기 한 건을 해제(삭제)한다. `deviceKey`가 없으면
     * [team.inreok.getiserver.domain.notification.exception.NotificationDeviceNotFoundException]
     * (404), 다른 회원이 등록한 기기면
     * [team.inreok.getiserver.domain.notification.exception.NotificationDeviceAccessDeniedException]
     * (403).
     */
    fun unregister(
        memberId: Long,
        deviceKey: String,
    )
}
