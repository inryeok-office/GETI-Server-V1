package team.inreok.getiserver.domain.inquiry.entity.type

// 공통 Discord Delivery 계약(discord_deliveries 테이블, PENDING/PROCESSING/DELIVERED/FAILED)이
// 아직 저장소에 없다(V18 시점 실측, discord_deliveries 테이블 부재 확정). 실제 Discord 연동은
// Phase 5(Notification 공개 Query Port 조회)에서 다루며, 그 전까지 모든 Inquiry API는
// [PENDING]만 반환한다 -- 실패를 뜻하지 않고, 조회 자체를 아직 연결하지 않았다는 뜻이다.
//
// Group A Q2에서 승인받은 범위는 "Query Port 계약 정의 + PENDING 고정"뿐이다. 이전 버전은
// 공통 계약과 이름·값 구성이 완전히 같은 `DiscordDeliveryStatus`였는데, 이는 두 가지 문제가
// 있었다(사용자 지적 반영, 재논의 완료).
// 1) 이름이 향후 도입될 공통 계약 Enum과 동일해 혼동 위험이 있었다 -- Program 도메인의 선례
//    (domain.program.entity.type.DiscordDeliveryStatus)는 값 구성(SUCCESS/FAILED/SKIPPED)이
//    공통 계약과 달라 이런 혼동이 없었다. Inquiry 쪽만 이름과 값이 모두 같아 더 위험했다.
// 2) PROCESSING/DELIVERED/FAILED는 실제로 반환한 적도, 반환할 계획도 없는 값이었다 -- 승인되지
//    않은 미래 계약을 미리 흉내낸 것에 불과했다.
// 그래서 이번에 Domain 이름을 접두어로 붙여 공통 계약과 구분하고, 값도 실제로 쓰는 PENDING
// 하나만 남겼다. Phase 5에서 Notification 도메인이 Discord를 실제로 연동할 때 이 Enum을 공통
// 계약(또는 그 조회 결과)으로 교체한다. Program이 Phase 7 교체 예정 경고를 남긴 것과 동일한
// 방식이다.
enum class InquiryDiscordDeliveryStatus {
    PENDING,
}
