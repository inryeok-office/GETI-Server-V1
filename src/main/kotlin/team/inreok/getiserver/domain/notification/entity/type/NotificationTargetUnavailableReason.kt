package team.inreok.getiserver.domain.notification.entity.type

/**
 * 알림이 가리키는 원본 리소스로 이동할 수 없는 이유다(원본 요구사항 문서 3절/17절). 원본이
 * 삭제되거나 접근할 수 없게 되어도 알림 Row 자체는 지우지 않고, 대신 응답에서 이 이유를 계산해
 * 내려준다 — 클라이언트가 원본의 공개·삭제·권한 상태를 자체 판단하지 않게 하기 위함이다.
 *
 * [FORBIDDEN]은 값만 정의하고 이번 범위에서는 사용하지 않는다. 원본 문서 17절이 예로 든 "MOU
 * Job에 접근 권한 없음"에 대응하는 규칙이 저장소에 없기 때문이다 — `MouStatus`는 `companies`의
 * 협약 상태일 뿐 공고 조회 권한이 아니고, 공고 조회 경로는 현재 인증만 요구한다. 소유자
 * 기반 권한이 있는 대상(JOB_APPLICATION 등)을 붙이는 시점에 사용한다(DECISION_REQUIRED로 보고).
 */
enum class NotificationTargetUnavailableReason {
    DELETED,
    NOT_VISIBLE,
    FORBIDDEN,
}
