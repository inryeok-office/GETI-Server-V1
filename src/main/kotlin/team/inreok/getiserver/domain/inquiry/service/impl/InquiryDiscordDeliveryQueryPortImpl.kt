package team.inreok.getiserver.domain.inquiry.service.impl

import org.springframework.stereotype.Component
import team.inreok.getiserver.domain.inquiry.entity.type.InquiryDiscordDeliveryStatus
import team.inreok.getiserver.domain.inquiry.service.InquiryDiscordDeliveryQueryPort

/**
 * discord_deliveries 테이블이 저장소에 없어(V18 시점 실측) 실제 조회를 시도하지 않고 항상
 * [InquiryDiscordDeliveryStatus.PENDING]을 반환하는 자리 표시자 구현체다(요구사항 §42, Phase 5).
 * 실제 Discord 연동이 시작되면(Notification 도메인이 discord_deliveries를 실제로 갖추면) 이
 * 구현체를 교체한다 -- [InquiryDiscordDeliveryQueryPort] 계약 자체는 바꾸지 않아도 된다.
 */
@Component
class InquiryDiscordDeliveryQueryPortImpl : InquiryDiscordDeliveryQueryPort {
    override fun statusOf(inquiryId: Long): InquiryDiscordDeliveryStatus = InquiryDiscordDeliveryStatus.PENDING
}
