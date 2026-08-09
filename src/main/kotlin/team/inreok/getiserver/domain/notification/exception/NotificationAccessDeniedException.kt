package team.inreok.getiserver.domain.notification.exception

import team.inreok.getiserver.global.error.BusinessException

/**
 * 다른 사용자의 알림에 접근했을 때 던진다. 어떤 알림인지·누구의 것인지는 응답 Message에 담지
 * 않는다(다른 사용자의 알림 존재 여부를 알려주지 않기 위해서다).
 */
class NotificationAccessDeniedException : BusinessException(NotificationErrorCode.NOTIFICATION_ACCESS_DENIED)
