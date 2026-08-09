package team.inreok.getiserver.domain.notification.entity.type

/**
 * 알림이 가리키는 원본 리소스의 종류다(원본 요구사항 문서 3절).
 *
 * 이 중 실제로 접근 가능 여부를 해석할 수 있는 것은 현재 [JOB]과 [PROGRAM]뿐이다
 * ([team.inreok.getiserver.domain.notification.service.NotificationTargetResolver] 참고).
 * [INQUIRY]와 [PORTFOLIO_REQUEST]는 해당 Domain에 Service 계층 자체가 없고(Entity/Repository만
 * 존재), [JOB_APPLICATION]과 [MEMBER_APPROVAL]은 Event 연결 시점에 함께 붙인다.
 */
enum class NotificationTargetType {
    JOB,
    JOB_APPLICATION,
    PROGRAM,
    PORTFOLIO_REQUEST,
    INQUIRY,
    MEMBER_APPROVAL,
}
