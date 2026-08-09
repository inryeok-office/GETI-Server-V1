package team.inreok.getiserver.domain.program.entity.type

// 원본 요구사항 문서가 정확한 값 목록을 정의하지 않아(6/7/8절 discordDelivery.status), Job
// 도메인에서 이미 쓰는 성공/실패 2분법에 SKIPPED(이번 Phase 범위에 Discord 실제 연동이 없어 시도
// 자체를 하지 않은 경우)를 더해 정의했다. 실제 Discord Webhook 연동은 Phase 7(별도 작업)에서
// 다룬다 — 그 전까지 모든 Program API는 SKIPPED만 반환한다.
//
// 경고(PR #81 리뷰 MINOR 지적): 이 값 구성(SUCCESS/FAILED/SKIPPED)은 최근 확정된 공통 Discord
// Delivery 계약(PENDING/PROCESSING/DELIVERED/FAILED, discord_deliveries Table)과 다르다. Phase
// 7에서 Notification 도메인이 Discord를 실제로 연동할 때 이 Enum을 공통 계약으로 교체해야 하며,
// 이는 Breaking Change(API 응답 discordDelivery.status의 실제 값 집합 변경)가 될 수 있다. 지금
// 당장 전면 교체하지 않은 이유는 Phase 7에서 Notification 도메인 실연동 시 다시 설계해야 하기
// 때문이다(코드 구조 자체는 이번에 바꾸지 않았다).
enum class DiscordDeliveryStatus {
    SUCCESS,
    FAILED,
    SKIPPED,
}
