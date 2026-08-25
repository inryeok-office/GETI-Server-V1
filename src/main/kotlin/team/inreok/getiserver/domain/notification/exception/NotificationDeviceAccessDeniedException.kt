package team.inreok.getiserver.domain.notification.exception

import team.inreok.getiserver.global.error.BusinessException

/**
 * 다른 회원이 등록한 기기를 해제하려 했을 때 던진다. 어떤 기기인지·누구의 것인지는 응답 Message에
 * 담지 않는다(다른 회원의 deviceKey 존재 여부를 알려주지 않기 위해서다,
 * [NotificationAccessDeniedException]과 같은 이유).
 */
class NotificationDeviceAccessDeniedException :
    BusinessException(NotificationErrorCode.NOTIFICATION_DEVICE_ACCESS_DENIED)
