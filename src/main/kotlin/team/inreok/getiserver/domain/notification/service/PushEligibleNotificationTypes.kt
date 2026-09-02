package team.inreok.getiserver.domain.notification.service

import team.inreok.getiserver.domain.notification.entity.type.NotificationType

/**
 * Push로 보낼 `NotificationType`의 확정 목록이다(Issue #190 작업 지시, #191 수신자 정책의 확정
 * 여부와 무관하게 Enum 값 자체로 판단한다). "사용자 행동/상태 변화와 직접 관련된 알림" 중심으로
 * 좁힌다는 Issue #190 원칙에 따라, 단순 정보성 변경 알림(JOB_UPDATED 등)은 제외한다.
 *
 * 포함: INQUIRY_ANSWERED, JOB_APPLICATION_STATUS_CHANGED, MEMBER_APPROVAL_RESULT,
 * PROGRAM_DELETED, JOB_PUBLISHED, PROGRAM_PUBLISHED, PROGRAM_APPLICATION_APPLIED,
 * PROGRAM_APPLICATION_CANCELED.
 *
 * 제외: JOB_UPDATED, JOB_CLOSED, JOB_DELETED, PROGRAM_UPDATED, PROGRAM_CLOSED,
 * PROGRAM_VACANCY_AVAILABLE, SYSTEM.
 */
object PushEligibleNotificationTypes {
    val TYPES: Set<NotificationType> =
        setOf(
            NotificationType.INQUIRY_ANSWERED,
            NotificationType.JOB_APPLICATION_STATUS_CHANGED,
            NotificationType.MEMBER_APPROVAL_RESULT,
            NotificationType.PROGRAM_DELETED,
            NotificationType.JOB_PUBLISHED,
            NotificationType.PROGRAM_PUBLISHED,
            NotificationType.PROGRAM_APPLICATION_APPLIED,
            NotificationType.PROGRAM_APPLICATION_CANCELED,
        )

    fun isEligible(type: NotificationType): Boolean = type in TYPES
}
