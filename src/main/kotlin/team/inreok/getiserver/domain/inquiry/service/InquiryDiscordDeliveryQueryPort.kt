package team.inreok.getiserver.domain.inquiry.service

import team.inreok.getiserver.domain.inquiry.entity.type.InquiryDiscordDeliveryStatus

/**
 * 문의의 Discord 접수 알림 전달 상태를 조회하는 계약이다(요구사항 §42, Phase 5). `InquiryServiceImpl`이
 * 응답을 만들 때 [InquiryDiscordDeliveryStatus.PENDING] Literal을 여러 곳에 직접 흩어 쓰지 않고
 * 이 Port 하나로 모아, 실제 Discord 연동이 시작될 때 구현체 하나만 교체하면 되게 한다.
 *
 * discord_deliveries 테이블이 아직 저장소에 없어(V18 시점 실측, 여전히 부재) 현재 유일한 구현체
 * ([team.inreok.getiserver.domain.inquiry.service.impl.InquiryDiscordDeliveryQueryPortImpl])는
 * 항상 [InquiryDiscordDeliveryStatus.PENDING]만 반환한다. Discord Worker/BotClient, 재시도
 * 로직은 이번 Phase 범위가 아니다(§42) -- 이 Port는 "조회"만 하고 "전송을 시도"하지 않는다.
 *
 * 원래 이 계약은 discord_deliveries를 실제로 소유할 `domain.notification`이 공개하는 형태로
 * 구상됐다([InquiryDiscordDeliveryStatus] KDoc 참고). 하지만 Phase 4에서 `domain.notification`이
 * 이미 `InquiryAnsweredEvent`를 구독해 `domain.notification -> domain.inquiry.event` 의존이
 * 생겼다. 이 상태에서 `domain.inquiry -> domain.notification` 의존을 추가하면 두 Module이
 * 서로를 참조하는 순환 의존이 되어 `ModularityTest`(`modules.verify()`)가 실패한다. 그래서 이
 * 계약은 `domain.inquiry` 내부에 두고 다른 Domain에 공개하지 않는다(Named Interface 아님) --
 * 실제 Discord 연동(discord_deliveries 테이블 도입) 시점에 소유권을 다시 검토한다.
 */
interface InquiryDiscordDeliveryQueryPort {
    fun statusOf(inquiryId: Long): InquiryDiscordDeliveryStatus
}
