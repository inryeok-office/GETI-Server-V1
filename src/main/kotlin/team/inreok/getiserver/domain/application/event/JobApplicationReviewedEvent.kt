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
 *
 * [historyId]는 Notification Idempotency(Issue #193)의 `sourceEventId`로 쓰인다 -- 발행 시점에
 * 이 값이 없어(기존에는 `applicationId`/`studentMemberId`/`action`/`reason`뿐이었다) 발행 측에
 * 새로 추가했다(`recordStatusHistory`가 저장한 [team.inreok.getiserver.domain.application.entity
 * .JobApplicationStatusHistory.id]를 그대로 담는다). `applicationId`만으로는 안정적인 식별자가
 * 되지 못한다 -- 같은 지원서가 REQUEST_REVISION 뒤 APPROVE처럼 여러 번 검토될 수 있는데, 그때마다
 * 서로 다른 검토 결과 알림이 나가야 하기 때문이다(같은 Event의 재처리가 아니라 서로 다른 상태
 * 전이). 이력 Row는 상태가 바뀔 때마다 하나씩 새로 생겨(Update/삭제 없음) 이 조건을 만족한다.
 */
@NamedInterface
data class JobApplicationReviewedEvent(
    val applicationId: Long,
    val studentMemberId: Long,
    /** `JobApplicationAdminAction.name` — ALLOW_EDIT/REQUEST_REVISION/APPROVE/REJECT 중 하나. */
    val action: String,
    /** REQUEST_REVISION/REJECT에서만 값이 있다(`JobApplicationAdminActionRequest` 계약과 동일). */
    val reason: String?,
    /** 이 검토로 새로 생긴 `JobApplicationStatusHistory`의 id. Notification Idempotency Identity의
     * `sourceEventId`로 쓰인다. */
    val historyId: Long,
)
