package team.inreok.getiserver.domain.member.event

import org.springframework.modulith.NamedInterface

/**
 * 교직원 가입 승인/거절 처리가 끝났음을 알리는 최소 계약이다(Issue #118, 제품 계약 Notification
 * §16.1의 "교직원 가입 승인 / 교직원 가입 거절" 최소 인앱 알림 대상). `domain.notification`이
 * 이 Event를 구독해 대상 회원에게 `MEMBER_APPROVAL_RESULT` 알림을 만든다.
 *
 * 발행 측([team.inreok.getiserver.domain.member.service.impl.MemberApprovalServiceImpl])은 승인/거절
 * Transaction 안에서 발행하고, 구독 측은 [team.inreok.getiserver.domain.inquiry.event.InquiryAnsweredEvent]와
 * 동일하게 `@TransactionalEventListener(phase = AFTER_COMMIT)`를 사용한다 -- 상태 전이가 Rollback되면
 * 알림도 만들어지지 않아야 하기 때문이다.
 *
 * `InquiryAnsweredEvent`와 같은 이유로 Entity를 담지 않고 Scalar 값만 담는다. `member`는 아직
 * `notification`이 쓸 수 있는 승인 결과 Query Port를 공개하지 않았고, 알림 문구에 필요한 값이
 * [approved]와 [rejectionReason] 둘뿐이라 다시 조회할 필요가 없다.
 */
@NamedInterface
data class MemberApprovalProcessedEvent(
    val memberId: Long,
    /** `true`면 승인(ACTIVE), `false`면 거절(REJECTED)이다. */
    val approved: Boolean,
    /** 거절 사유. 승인일 때는 항상 null이다(거절은 사유가 필수라 항상 non-null). */
    val rejectionReason: String?,
)
