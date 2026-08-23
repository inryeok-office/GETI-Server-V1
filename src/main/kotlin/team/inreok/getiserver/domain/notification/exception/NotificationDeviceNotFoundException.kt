package team.inreok.getiserver.domain.notification.exception

import team.inreok.getiserver.global.error.BusinessException

class NotificationDeviceNotFoundException(
    deviceKey: String,
) : BusinessException(
        NotificationErrorCode.NOTIFICATION_DEVICE_NOT_FOUND,
        "요청한 기기를 찾을 수 없습니다. (deviceKey=$deviceKey)",
    )
