package team.inreok.getiserver.domain.program.dto

import io.swagger.v3.oas.annotations.media.Schema
import team.inreok.getiserver.domain.program.entity.type.ProgramStatus
import java.time.LocalDateTime

/**
 * 프로그램 상태 변경 응답이다(원본 요구사항 문서 8절). `notificationCount`/
 * `expiredVacancySubscriptionCount`는 Notification/빈자리 구독 기능이 아직 없어(각각 Phase 6)
 * 항상 0을 반환한다(허위 값 반환 금지).
 */
@Schema(description = "프로그램 상태 변경 응답")
data class ProgramStatusUpdateResponse(
    @param:Schema(description = "프로그램 ID", example = "1")
    val programId: Long,
    @param:Schema(description = "변경된 프로그램 상태", example = "DELETED")
    val status: ProgramStatus,
    @param:Schema(description = "담당 교사", nullable = true)
    val manager: ProgramManagerSummary?,
    @param:Schema(description = "신청자에게 생성한 알림 수. Notification 기능 미구현으로 항상 0", example = "0")
    val notificationCount: Int,
    @param:Schema(description = "만료 처리한 활성 빈자리 구독 수. 구독 기능 미구현으로 항상 0", example = "0")
    val expiredVacancySubscriptionCount: Int,
    @param:Schema(description = "수정 시각", example = "2026-08-03T09:00:00", nullable = true)
    val updatedAt: LocalDateTime?,
)
