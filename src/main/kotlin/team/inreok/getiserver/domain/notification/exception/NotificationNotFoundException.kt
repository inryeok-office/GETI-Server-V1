package team.inreok.getiserver.domain.notification.exception

import team.inreok.getiserver.global.error.BusinessException

class NotificationNotFoundException(
    notificationId: Long,
) : BusinessException(
        NotificationErrorCode.NOTIFICATION_NOT_FOUND,
        "요청한 알림을 찾을 수 없습니다. (notificationId=$notificationId)",
    )
