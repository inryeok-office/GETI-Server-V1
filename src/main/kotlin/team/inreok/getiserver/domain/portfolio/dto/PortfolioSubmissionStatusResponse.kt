package team.inreok.getiserver.domain.portfolio.dto

import io.swagger.v3.oas.annotations.media.Schema
import team.inreok.getiserver.domain.portfolio.entity.type.PortfolioMaterialType
import team.inreok.getiserver.domain.portfolio.entity.type.PortfolioSubmissionStatus
import java.time.LocalDateTime

/**
 * 관리자 제출 현황 목록의 한 학생 항목이다(요구사항 §19, Issue #204 Phase 2b).
 *
 * 대상 학생 전체를 기준으로 하므로, 아직 제출하지 않은 학생도 이 목록에 나온다 -- 그 경우
 * [submitted]는 false, [status]/[materialType]/[submittedAt]은 null이다. [submitted]는 실제
 * 제출 완료(SUBMITTED)만 true로 본다. 임시저장(DRAFT)은 제출로 세지 않는다(목록의 submittedCount
 * 집계와 같은 기준, §20).
 *
 * 학번(studentNumber)은 넣지 않는다 -- `members` 실제 스키마에 해당 Column이 없다(기존
 * MemberApplicantSnapshot과 동일한 판단). 대신 기수([cohort])와 학과([department])를 제공한다.
 */
@Schema(description = "관리자 제출 현황 목록 항목")
data class PortfolioSubmissionStatusResponse(
    @param:Schema(description = "대상 학생 회원 ID", example = "7")
    val memberId: Long,
    @param:Schema(description = "학생 이름. 이름이 없으면 null이다.", example = "홍길동", nullable = true)
    val studentName: String?,
    @param:Schema(description = "기수. 값이 없으면 null이다.", example = "6", nullable = true)
    val cohort: Int?,
    @param:Schema(description = "학과(DepartmentType). 값이 없으면 null이다.", example = "SW_DEVELOPMENT", nullable = true)
    val department: String?,
    @param:Schema(description = "제출 완료(SUBMITTED) 여부. 미제출·임시저장은 false다.", example = "true")
    val submitted: Boolean,
    @param:Schema(
        description = "제출물 상태. 제출물이 없으면(미제출) null이다.",
        example = "SUBMITTED",
        nullable = true,
    )
    val status: PortfolioSubmissionStatus?,
    @param:Schema(
        description = "제출 자료 종류. 파일만 FILE, URL만 URL, 둘 다면 BOTH. 자료가 없거나 미제출이면 null이다.",
        example = "BOTH",
        nullable = true,
    )
    val materialType: PortfolioMaterialType?,
    @param:Schema(
        description = "제출 확정 시각. 임시저장만 했거나 미제출이면 null이다.",
        example = "2026-09-20T10:00:00",
        nullable = true,
    )
    val submittedAt: LocalDateTime?,
)
