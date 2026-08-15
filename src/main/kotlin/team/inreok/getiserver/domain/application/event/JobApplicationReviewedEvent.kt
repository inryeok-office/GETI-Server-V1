package team.inreok.getiserver.domain.application.event

import org.springframework.modulith.NamedInterface

/**
 * 교사·개발자의 지원서 검토 Action(ALLOW_EDIT/REQUEST_REVISION/APPROVE/REJECT)이 성공했음을
 * 알리는 최소 계약이다(Issue #135). `domain.notification`이 이 Event를 구독해 지원자 본인에게
 * `JOB_APPLICATION_STATUS_CHANGED` 알림을 만든다.
 *
 * 발행 측([team.inreok.getiserver.domain.application.service.impl.JobApplicationAdminServiceImpl])은
 * 상태 전이·이력 기록과 같은 `@Transactional` 안에서 발행하고, 구독 측은
 * [team.inreok.getiserver.domain.member.event.MemberApprovalProcessedEvent]와 동일하게
 * `@TransactionalEventListener(phase = AFTER_COMMIT)`를 사용한다 -- 검토 Action이 Rollback되면
 * 알림도 만들어지지 않아야 하기 때문이다.
 *
 * [action]을 `JobApplicationAdminAction`(Enum) 대신 `.name` 문자열로 받는 이유는
 * `JobApplicationStatusHistory.action`과 같다 -- 서로 다른 Domain이 이 값을 다루므로 Enum
 * 자체를 공개하지 않고 원시 값만 넘긴다(`JobApplicationHistoryRecording.kt` KDoc 참고).
 * DECISION_REQUIRED로 확인된 결과, 학생 Action(SUBMIT/RESUBMIT/REQUEST_EDIT)에 대한 담당 교사
 * 알림은 이번 Phase 범위에 포함하지 않는다(사용자 확인 완료) -- 이 Event는 교사 검토 Action에서만
 * 발행된다.
 */
@NamedInterface
data class JobApplicationReviewedEvent(
    val applicationId: Long,
    val studentMemberId: Long,
    /** `JobApplicationAdminAction.name` — ALLOW_EDIT/REQUEST_REVISION/APPROVE/REJECT 중 하나. */
    val action: String,
    /** REQUEST_REVISION/REJECT에서만 값이 있다(`JobApplicationAdminActionRequest` 계약과 동일). */
    val reason: String?,
)
