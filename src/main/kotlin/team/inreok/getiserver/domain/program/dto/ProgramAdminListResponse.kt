package team.inreok.getiserver.domain.program.dto

import io.swagger.v3.oas.annotations.media.Schema
import team.inreok.getiserver.domain.program.entity.Program
import team.inreok.getiserver.domain.program.entity.type.ProgramStatus
import team.inreok.getiserver.domain.program.entity.type.ProgramType
import java.time.LocalDateTime

@Schema(description = "관리자 프로그램 목록 결과. page는 0부터 시작합니다.")
data class ProgramAdminListResponse(
    @param:Schema(description = "관리자 프로그램 목록")
    val content: List<ProgramAdminListItemResponse>,
    @param:Schema(description = "현재 페이지 번호(0부터 시작)", example = "0")
    val page: Int,
    @param:Schema(description = "페이지 크기", example = "20")
    val size: Int,
    @param:Schema(description = "전체 결과 개수", example = "42")
    val totalElements: Long,
    @param:Schema(description = "전체 페이지 수", example = "3")
    val totalPages: Int,
    @param:Schema(description = "첫 페이지 여부", example = "true")
    val first: Boolean,
    @param:Schema(description = "마지막 페이지 여부", example = "false")
    val last: Boolean,
)

@Schema(description = "관리자 프로그램 목록 항목")
data class ProgramAdminListItemResponse(
    @param:Schema(description = "프로그램 ID", example = "1")
    val programId: Long,
    @param:Schema(description = "프로그램 제목", example = "2026 여름방학 백엔드 특강")
    val title: String,
    @param:Schema(description = "프로그램 유형", example = "SPECIAL_LECTURE")
    val programType: ProgramType,
    @param:Schema(description = "프로그램 상태", example = "DRAFT")
    val status: ProgramStatus,
    @param:Schema(description = "행사 시작 일시", nullable = true)
    val startAt: LocalDateTime?,
    @param:Schema(description = "행사 종료 일시", nullable = true)
    val endAt: LocalDateTime?,
    @param:Schema(description = "신청 시작 일시", nullable = true)
    val applicationStartAt: LocalDateTime?,
    @param:Schema(description = "신청 종료 일시", nullable = true)
    val applicationEndAt: LocalDateTime?,
    @param:Schema(description = "모집 정원", nullable = true)
    val capacity: Int?,
    @param:Schema(description = "생성 일시", nullable = true)
    val createdAt: LocalDateTime?,
    @param:Schema(description = "최종 수정 일시", nullable = true)
    val updatedAt: LocalDateTime?,
) {
    companion object {
        fun from(program: Program): ProgramAdminListItemResponse =
            ProgramAdminListItemResponse(
                programId = requireNotNull(program.id),
                title = program.title,
                programType = program.type,
                status = program.status,
                startAt = program.eventStartedAt,
                endAt = program.eventEndedAt,
                applicationStartAt = program.applicationStartedAt,
                applicationEndAt = program.applicationEndedAt,
                capacity = program.capacity,
                createdAt = program.createdAt,
                updatedAt = program.updatedAt,
            )
    }
}
