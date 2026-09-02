package team.inreok.getiserver.domain.portfolio.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

/**
 * 포트폴리오 수합 요청 부분 수정 요청이다(요구사항 §8). PATCH 원칙에 따라 전달하지 않은(null)
 * Field는 기존 값을 유지한다 -- 예: `targetStudentIds == null`이면 대상 학생을 그대로 둔다.
 *
 * 대상 학생 교체(`targetStudentIds` 지정)는 DRAFT에서만 허용한다(§27의 고아 Submission 문제를
 * 피하기 위한 Phase 1 기본값). CLOSED/DELETED 요청은 어떤 Field도 수정할 수 없다.
 *
 * `fileIds`는 File Integration(Phase 3)에서 추가한다.
 */
@Schema(description = "포트폴리오 수합 요청 수정 요청(전달한 Field만 반영)")
data class PortfolioRequestUpdateRequest(
    @field:Size(max = 500, message = "수합 요청 제목은 500자를 넘을 수 없습니다.")
    @param:Schema(description = "수합 요청 제목. 생략하면 유지", example = "2026 상반기 포트폴리오 수합", nullable = true, maxLength = 500)
    val title: String? = null,
    @param:Schema(description = "수합 요청 설명. 생략하면 유지", example = "졸업 포트폴리오를 제출해 주세요.", nullable = true)
    val description: String? = null,
    @param:Schema(description = "제출 마감 시각. 생략하면 유지", example = "2026-09-30T23:59:59", nullable = true)
    val dueAt: LocalDateTime? = null,
    // 등록 요청과 같은 이유로 상한을 둔다(대량 IN 조회·INSERT 방어선).
    @field:Size(max = 1000, message = "제출 대상 학생은 1000명을 넘을 수 없습니다.")
    @param:Schema(
        description = "제출 대상 학생 memberId 목록(최대 1000명). 생략하면 유지하고, 지정하면 전체를 교체한다(DRAFT에서만 허용).",
        example = "[1, 2, 3]",
        nullable = true,
    )
    val targetStudentIds: List<Long>? = null,
)
