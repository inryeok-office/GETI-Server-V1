package team.inreok.getiserver.domain.notification.entity.type

/**
 * 알림이 가리키는 원본 리소스의 종류다(원본 요구사항 문서 3절).
 *
 * [MEMBER_APPROVAL]을 제외한 모든 유형은 접근 가능 여부를 해석한다
 * ([team.inreok.getiserver.domain.notification.service.NotificationTargetResolver] 참고).
 * [MEMBER_APPROVAL]은 승인 결과를 보여줄 상세 화면이 없어 아직 해석하지 않는다.
 */
enum class NotificationTargetType {
    JOB,
    JOB_APPLICATION,
    PROGRAM,
    PORTFOLIO_REQUEST,
    INQUIRY,
    MEMBER_APPROVAL,
}
