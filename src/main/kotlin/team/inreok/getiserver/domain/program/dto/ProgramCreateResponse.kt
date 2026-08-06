package team.inreok.getiserver.domain.program.dto

import io.swagger.v3.oas.annotations.media.Schema
import team.inreok.getiserver.domain.program.entity.type.ProgramStatus
import team.inreok.getiserver.domain.program.entity.type.ProgramType
import java.time.LocalDateTime

@Schema(description = "프로그램 등록·임시저장 응답")
data class ProgramCreateResponse(
    @param:Schema(description = "프로그램 ID", example = "1")
    val programId: Long,
    @param:Schema(description = "프로그램 유형", example = "SPECIAL_LECTURE")
    val programType: ProgramType,
    @param:Schema(description = "대상 학년 목록", example = "[2, 3]")
    val targetGrades: List<Int>,
    @param:Schema(description = "모집 정원", example = "20", nullable = true)
    val capacity: Int?,
    @param:Schema(description = "현재 활성 신청 인원", example = "0")
    val currentApplicants: Int,
    @param:Schema(description = "잔여 정원. capacity가 없으면 null", example = "20", nullable = true)
    val remainingCapacity: Int?,
    @param:Schema(description = "선착순 모집 여부(항상 true)", example = "true")
    val firstComeServed: Boolean,
    @param:Schema(description = "담당 교사. PUBLISHED로 등록해야 채워지고, DRAFT면 null", nullable = true)
    val manager: ProgramManagerSummary?,
    @param:Schema(description = "프로그램 상태", example = "DRAFT")
    val status: ProgramStatus,
    val discordDelivery: DiscordDeliveryResult,
    @param:Schema(description = "생성 시각", example = "2026-08-01T09:00:00", nullable = true)
    val createdAt: LocalDateTime?,
)
