package team.inreok.getiserver.domain.notification.exception

import team.inreok.getiserver.global.error.BusinessException

/**
 * `scope=SINGLE`인데 `notificationId`가 없을 때 던진다. Bean Validation으로는 "scope에 따라
 * 달라지는 필수 여부"를 표현할 수 없어 Service에서 검증한다
 * (`MemberApprovalServiceImpl.reject`의 `RejectionReasonRequiredException`과 같은 방식).
 */
class NotificationIdRequiredException : BusinessException(NotificationErrorCode.NOTIFICATION_ID_REQUIRED)
