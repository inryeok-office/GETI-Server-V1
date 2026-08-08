package team.inreok.getiserver.domain.inquiry.event

import org.springframework.modulith.NamedInterface
import team.inreok.getiserver.domain.inquiry.entity.type.InquiryType
import java.time.LocalDateTime

/**
 * 문의가 등록됐음을 알리는 최소 계약이다(요구사항 §5, Phase 5). 문의 등록 Transaction이 Commit된
 * 뒤에만 실제로 소비되도록, 발행 측(`InquiryServiceImpl.create`)은 이 Event를 Transaction 안에서
 * 발행하고 구독 측은 [InquiryAnsweredEvent]와 동일하게
 * `@TransactionalEventListener(phase = AFTER_COMMIT)`를 사용해야 한다.
 *
 * 이번 Phase는 이 Event를 실제로 구독하는 Listener를 만들지 않는다 -- Discord Worker/BotClient는
 * §42에 따라 이번 범위에서 만들지 않고, "새 문의 접수" 인앱 알림 Type도 Notification 원본
 * 요구사항 문서 3절이 확정한 15개 값(`NotificationType` KDoc 참고)에 없어 임의로 추가하지
 * 않았다. 이 Event는 향후 그런 Listener가 생길 때 쓸 수 있도록 미리 공개해 둔 Hook Point다.
 *
 * [InquiryAnsweredEvent]와 같은 이유로 Entity를 담지 않고 ID·Scalar 값만 담는다.
 */
@NamedInterface
data class InquiryCreatedEvent(
    val inquiryId: Long,
    val inquiryType: InquiryType,
    val title: String,
    val authorMemberId: Long,
    val occurredAt: LocalDateTime,
)
