package team.inreok.getiserver.domain.program.dto

import io.swagger.v3.oas.annotations.media.Schema
import team.inreok.getiserver.domain.program.entity.type.ProgramStatus
import team.inreok.getiserver.domain.program.entity.type.ProgramType
import java.time.LocalDateTime

/**
 * 프로그램 목록 항목이다(원본 요구사항 문서 9절). `applied`는 요청한 학생의 신청 여부를 서버가
 * 계산한다. 학생이 아닌 요청(교사·개발자)은 항상 false를 반환한다 — API 계약이 Boolean으로
 * 고정돼 있어 nullable로 바꾸지 않았다(원본 요구사항 9절, 계약을 임의로 바꾸지 않는다는 원칙).
 */
@Schema(description = "프로그램 목록 항목")
data class ProgramSummaryResponse(
    @param:Schema(description = "프로그램 ID", example = "1")
    val programId: Long,
    @param:Schema(description = "프로그램 제목", example = "2026 여름방학 백엔드 특강")
    val title: String,
    @param:Schema(description = "프로그램 유형", example = "SPECIAL_LECTURE")
    val programType: ProgramType,
    @param:Schema(description = "프로그램 상태", example = "PUBLISHED")
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
    @param:Schema(description = "현재 활성 신청 인원", example = "18")
    val currentApplicants: Int,
    @param:Schema(description = "잔여 정원. capacity가 없으면 null", nullable = true)
    val remainingCapacity: Int?,
    @param:Schema(description = "선착순 모집 여부(항상 true)", example = "true")
    val firstComeServed: Boolean,
    @param:Schema(description = "요청한 학생의 신청 여부. 학생이 아니면 항상 false", example = "false")
    val applied: Boolean,
)
