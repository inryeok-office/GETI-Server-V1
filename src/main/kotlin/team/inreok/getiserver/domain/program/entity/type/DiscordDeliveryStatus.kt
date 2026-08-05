package team.inreok.getiserver.domain.program.entity.type

// 원본 요구사항 문서가 정확한 값 목록을 정의하지 않아(6/7/8절 discordDelivery.status), Job
// 도메인에서 이미 쓰는 성공/실패 2분법에 SKIPPED(이번 Phase 범위에 Discord 실제 연동이 없어 시도
// 자체를 하지 않은 경우)를 더해 정의했다. 실제 Discord Webhook 연동은 Phase 7(별도 작업)에서
// 다룬다 — 그 전까지 모든 Program API는 SKIPPED만 반환한다.
enum class DiscordDeliveryStatus {
    SUCCESS,
    FAILED,
    SKIPPED,
}
