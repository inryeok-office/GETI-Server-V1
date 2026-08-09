package team.inreok.getiserver.domain.program.dto

import io.swagger.v3.oas.annotations.media.Schema
import team.inreok.getiserver.domain.program.entity.type.DiscordDeliveryStatus

/**
 * Discord 게시·수정·삭제 결과다(원본 요구사항 문서 18절). Discord 실제 연동(Webhook 호출)은
 * Phase 7 범위라 이번 Phase의 모든 Program API는 항상 [DiscordDeliveryStatus.SKIPPED]를
 * 반환한다 — 실패를 뜻하지 않고, 연동 자체를 시도하지 않았다는 뜻이다.
 *
 * 경고(PR #81 리뷰 MINOR 지적): 이 DTO의 형태(status/failureReason)와 [DiscordDeliveryStatus]
 * 값 구성은 최근 확정된 공통 Discord Delivery 계약(PENDING/PROCESSING/DELIVERED/FAILED,
 * discord_deliveries Table)과 다르다. Phase 7에서 Notification 도메인이 Discord를 실제로
 * 연동할 때 이 DTO를 공통 계약에 맞춰 다시 설계해야 하며, 이는 Program API 응답의 `discordDelivery`
 * Field 형태가 바뀌는 Breaking Change가 될 수 있다. 지금은 구조를 바꾸지 않는다.
 */
@Schema(description = "Discord 게시 결과. Discord 실패는 Program 저장/상태변경 자체를 막지 않는다.")
data class DiscordDeliveryResult(
    @param:Schema(description = "Discord 게시 결과 상태", example = "SKIPPED")
    val status: DiscordDeliveryStatus,
    @param:Schema(description = "실패 사유. 성공하거나 시도하지 않았으면 null", nullable = true)
    val failureReason: String?,
) {
    companion object {
        // Phase 7(Discord 실제 연동) 전까지 모든 호출부가 공유하는 고정값이다.
        val SKIPPED_NOT_IMPLEMENTED =
            DiscordDeliveryResult(
                status = DiscordDeliveryStatus.SKIPPED,
                failureReason = "Discord 연동은 아직 구현되지 않았습니다(Phase 7 범위).",
            )
    }
}
