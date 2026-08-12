package team.inreok.getiserver.domain.inquiry.event

import org.springframework.modulith.NamedInterface

/**
 * 문의에 답변이 등록됐음을 알리는 최소 계약이다(요구사항 §5/§38). `domain.notification`이
 * 이 Event를 구독해 문의 작성자에게 인앱 알림을 만든다(Notification 도메인 개발 요구사항 5절).
 *
 * [team.inreok.getiserver.domain.job.event.JobChangedEvent]는 대상 id만 담고 구독 측이
 * 다시 조회하는 방식이지만, 이 Event는 [inquiryTitle]/[inquiryAuthorMemberId]까지 함께 담는다.
 * 이 Event가 만들어진 시점에는 `domain.inquiry`가 Notification이 쓸 수 있는 Query Port를 아직
 * 공개하지 않았다(그 뒤 도입된 [team.inreok.getiserver.domain.inquiry.query.InquiryDiscordPayloadQueryPort]는
 * 문의 접수(`INQUIRY_CREATED`) 용도이지 답변 알림 용도가 아니다). Entity는 담지 않고 Scalar
 * 값만 담아 Module 경계를 지킨다(사용자 확인 완료).
 */
@NamedInterface
data class InquiryAnsweredEvent(
    val inquiryId: Long,
    val answerId: Long,
    val inquiryAuthorMemberId: Long,
    val inquiryTitle: String,
)
