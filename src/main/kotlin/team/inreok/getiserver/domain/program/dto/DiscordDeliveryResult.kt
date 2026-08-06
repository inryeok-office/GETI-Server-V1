package team.inreok.getiserver.domain.program.dto

import io.swagger.v3.oas.annotations.media.Schema
import team.inreok.getiserver.domain.program.entity.type.DiscordDeliveryStatus

/**
 * Discord 게시·수정·삭제 결과다(원본 요구사항 문서 18절). Discord 실제 연동(Webhook 호출)은
 * Phase 7 범위라 이번 Phase의 모든 Program API는 항상 [DiscordDeliveryStatus.SKIPPED]를
 * 반환한다 — 실패를 뜻하지 않고, 연동 자체를 시도하지 않았다는 뜻이다.
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
