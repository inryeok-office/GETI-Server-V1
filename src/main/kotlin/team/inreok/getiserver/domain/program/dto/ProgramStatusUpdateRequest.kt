package team.inreok.getiserver.domain.program.dto

import io.swagger.v3.oas.annotations.media.Schema
import team.inreok.getiserver.domain.program.entity.type.ProgramStatus

/**
 * 프로그램 상태 변경 요청이다(원본 요구사항 문서 8절). 허용 전이는 다음과 같고, 그 외
 * (조기 마감, 마감 후 재개, 삭제 복구 포함)는 거부한다.
 *
 * ```text
 * DRAFT     -> PUBLISHED | DELETED
 * PUBLISHED -> DELETED
 * CLOSED    -> DELETED
 * ```
 *
 * `PUBLISHED -> CLOSED`는 이 API로 지정할 수 없다 — 신청 종료 시각 도달 시 서버 Scheduler
 * (`ProgramCloseScheduler`)가 자동으로 처리한다(Phase 7).
 */
@Schema(description = "프로그램 상태 변경 요청")
data class ProgramStatusUpdateRequest(
    @param:Schema(description = "변경할 상태", example = "PUBLISHED", allowableValues = ["PUBLISHED", "DELETED"])
    val status: ProgramStatus,
)
