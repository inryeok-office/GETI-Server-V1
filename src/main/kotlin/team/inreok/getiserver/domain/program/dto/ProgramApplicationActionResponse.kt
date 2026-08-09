package team.inreok.getiserver.domain.program.dto

import io.swagger.v3.oas.annotations.media.Schema
import team.inreok.getiserver.domain.program.entity.type.ProgramApplicationStatus
import java.time.LocalDateTime

/**
 * 프로그램 신청·취소 응답이다(원본 요구사항 문서 11절). `vacancyNotificationCount`는 CANCEL로
 * 빈자리가 생겨 구독자에게 실제로 알림을 만든 수를 뜻한다 — 구독 기능이 아직 없어(Phase 6)
 * 이번 Phase는 항상 0을 반환한다.
 */
@Schema(description = "프로그램 신청·취소 응답")
data class ProgramApplicationActionResponse(
    @param:Schema(description = "신청 ID", example = "1")
    val applicationId: Long,
    @param:Schema(description = "프로그램 ID", example = "1")
    val programId: Long,
    @param:Schema(description = "신청 상태", example = "APPLIED")
    val status: ProgramApplicationStatus,
    @param:Schema(description = "현재 활성 신청 인원", example = "20")
    val currentApplicants: Int,
    @param:Schema(description = "잔여 정원. capacity가 없으면 null", nullable = true)
    val remainingCapacity: Int?,
    @param:Schema(description = "현재 상태에서 수행할 수 있는 Action 목록", example = "[\"CANCEL\"]")
    val availableActions: List<String>,
    @param:Schema(description = "CANCEL로 새로 생성한 빈자리 알림 수. 구독 기능 미구현으로 항상 0", example = "0")
    val vacancyNotificationCount: Int,
    @param:Schema(description = "처리 시각", nullable = true)
    val updatedAt: LocalDateTime?,
)
