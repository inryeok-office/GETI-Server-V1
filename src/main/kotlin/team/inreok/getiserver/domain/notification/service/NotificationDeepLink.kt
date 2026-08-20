package team.inreok.getiserver.domain.notification.service

import team.inreok.getiserver.domain.notification.entity.type.NotificationTargetType

/**
 * 알림에서 원본 리소스로 이동할 경로를 만드는 유일한 지점이다.
 *
 * **가정(DECISION_REQUIRED)**: 절대 URL이 아니라 상대 경로를 돌려준다. 저장소에 프론트엔드
 * base URL 설정이 전혀 없고, 웹/앱 라우트 규약도 확정되지 않았기 때문이다. 클라이언트가 자신의
 * base를 앞에 붙여 쓴다. 규약이 확정되면 이 파일만 고치면 되도록 경로 규칙을 여기에 모았다.
 * 경로 이름은 각 리소스의 API 경로(`/api/v1/jobs`, `/api/v1/job-applications` 등)를 그대로 따른다.
 *
 * 경로를 만들 수 없는 경우(대상 없음, 아직 지원하지 않는 [NotificationTargetType])에는 null을
 * 돌려주고, 실제로 링크를 내려줄지는 접근 가능 여부까지 확인한 [NotificationTargetResolver]가
 * 결정한다.
 */
object NotificationDeepLink {
    fun of(
        targetType: NotificationTargetType?,
        targetId: Long?,
    ): String? {
        if (targetType == null || targetId == null) return null
        return when (targetType) {
            NotificationTargetType.JOB -> "/jobs/$targetId"

            NotificationTargetType.PROGRAM -> "/programs/$targetId"

            NotificationTargetType.INQUIRY -> "/inquiries/$targetId"

            NotificationTargetType.JOB_APPLICATION -> "/job-applications/$targetId"

            // 접근 가능 여부를 아직 계산할 수 없는 대상이라 이동 경로도 내려주지 않는다
            // (NotificationTargetResolver KDoc 참고).
            NotificationTargetType.PORTFOLIO_REQUEST,
            NotificationTargetType.MEMBER_APPROVAL,
            -> null
        }
    }
}
