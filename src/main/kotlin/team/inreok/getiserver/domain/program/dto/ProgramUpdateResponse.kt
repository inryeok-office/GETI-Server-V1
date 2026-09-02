package team.inreok.getiserver.domain.program.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

/**
 * 프로그램 수정 응답이다(원본 요구사항 문서 7절). `vacancyNotificationCount`/`notificationCreated`는
 * 정원 증가로 빈자리가 생겨 구독자에게 실제로 알림을 만들었는지를 뜻한다 — Notification 도메인에
 * 아직 Service 계층이 없어(entity/repository만 존재) 이번 Phase는 항상 0/false를 반환한다(허위
 * true 반환 금지, 요구사항 17절).
 */
@Schema(description = "프로그램 수정 응답")
data class ProgramUpdateResponse(
    @param:Schema(description = "프로그램 ID", example = "1")
    val programId: Long,
    @param:Schema(description = "모집 정원", example = "25", nullable = true)
    val capacity: Int?,
    @param:Schema(description = "현재 활성 신청 인원", example = "20")
    val currentApplicants: Int,
    @param:Schema(description = "잔여 정원. capacity가 없으면 null", example = "5", nullable = true)
    val remainingCapacity: Int?,
    @param:Schema(description = "정원 증가로 새로 생성한 빈자리 알림 수. 구독 기능 미구현으로 항상 0", example = "0")
    val vacancyNotificationCount: Int,
    @param:Schema(description = "빈자리 알림이 실제로 생성됐는지 여부. 구독 기능 미구현으로 항상 false", example = "false")
    val notificationCreated: Boolean,
    @param:Schema(description = "수정 시각", example = "2026-08-02T09:00:00", nullable = true)
    val updatedAt: LocalDateTime?,
)
