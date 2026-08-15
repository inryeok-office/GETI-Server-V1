package team.inreok.getiserver.domain.notification.event

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import team.inreok.getiserver.domain.inquiry.event.InquiryCreatedEvent
import team.inreok.getiserver.domain.notification.dto.DiscordDeliveryEnqueueCommand
import team.inreok.getiserver.domain.notification.entity.type.DiscordMessageTemplate
import team.inreok.getiserver.domain.notification.service.DiscordDeliveryService
import team.inreok.getiserver.global.discord.DiscordChannelResolver

/**
 * Inquiry가 발행하는 기존 [InquiryCreatedEvent]를 받아 Discord Delivery를 예약한다(Notification
 * 후속 요구사항 문서 §28, `docs/notification/discord-event-wiring-plan.md` §4.2). 새 Event를
 * 만들지 않고 이미 공개돼 있던(구독자 없이 대기 중이던) Event를 그대로 구독한다.
 *
 * 문의는 사용자가 채널을 선택하지 않고 고정 채널을 쓰며(요구사항 §9), Mention도 하지 않는다
 * (Discord 관리자 Alert일 뿐 학년 대상 알림이 아니다). Job/Program Listener와 달리 대상 조회
 * Port를 부르지 않는다 -- 채널이 대상 값과 무관하게 고정이라 조회할 이유가 없고, 이 Event는
 * `AFTER_COMMIT`에만 전달되므로 문의는 이미 저장되어 있음이 보장된다.
 */
@Component
class InquiryDiscordEventListener(
    private val discordChannelResolver: DiscordChannelResolver,
    private val discordDeliveryService: DiscordDeliveryService,
) {
    private val log = LoggerFactory.getLogger(InquiryDiscordEventListener::class.java)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onInquiryCreated(event: InquiryCreatedEvent) {
        runCatching { process(event) }
            .onFailure { ex ->
                log.error("문의 접수 Discord Delivery 예약 중 처리되지 않은 오류(inquiryId={})", event.inquiryId, ex)
            }
    }

    private fun process(event: InquiryCreatedEvent) {
        val channelId = discordChannelResolver.resolveInquiryChannelId()
        if (channelId == null) {
            log.warn("Discord 채널이 설정되지 않아 Delivery를 생성하지 않습니다(inquiryId={})", event.inquiryId)
            return
        }

        discordDeliveryService.enqueue(
            DiscordDeliveryEnqueueCommand(
                template = DiscordMessageTemplate.INQUIRY_CREATED,
                targetId = event.inquiryId,
                channelId = channelId,
            ),
        )
    }
}
