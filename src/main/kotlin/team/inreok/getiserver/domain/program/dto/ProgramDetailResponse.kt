package team.inreok.getiserver.domain.program.dto

import io.swagger.v3.oas.annotations.media.Schema
import team.inreok.getiserver.domain.program.entity.type.ProgramApplicationEligibilityReason
import team.inreok.getiserver.domain.program.entity.type.ProgramStatus
import team.inreok.getiserver.domain.program.entity.type.ProgramType
import java.time.LocalDateTime

/**
 * 프로그램 상세 응답이다(원본 요구사항 문서 10절). `canApply`/`eligibilityReason`/
 * `eligibilityMessage`/`availableActions`/`canSubscribeVacancy`/`vacancySubscribed`/
 * `vacancySubscriptionStatus`는 요청한 학생 기준으로 서버가 계산한다 — 교사·개발자 요청은 학생이
 * 아니므로 `canApply=false`, `eligibilityReason=NOT_ENROLLED`로 응답한다(재학 상태가 없기 때문에
 * 실제 판정과 같은 경로를 그대로 탄다).
 *
 * `canSubscribeVacancy`/`vacancySubscribed`/`vacancySubscriptionStatus`는 빈자리 구독 기능이
 * 아직 없어(Phase 6) 이번 Phase는 항상 false/false/null을 반환한다.
 *
 * [files]는 DRAFT 상태에서는 등록자·담당 교사·개발자에게만 실제 목록을 반환하고, 그 외 요청자에게는
 * 빈 목록을 반환한다(`team.inreok.getiserver.domain.program.access.canViewProgramFiles`와 동일한
 * 규칙, `ProgramFileAccessChecker`의 다운로드 권한 판정과 일관성을 맞추기 위함, Issue #127). 이
 * 응답의 다른 Field(`title`/`content` 등)는 아직 이 규칙을 적용하지 않는다 — 별도 개선 대상이다.
 */
@Schema(description = "프로그램 상세 정보")
data class ProgramDetailResponse(
    @param:Schema(description = "프로그램 ID", example = "1")
    val programId: Long,
    @param:Schema(description = "프로그램 제목", example = "2026 여름방학 백엔드 특강")
    val title: String,
    @param:Schema(description = "Markdown 본문", nullable = true)
    val content: String?,
    @param:Schema(description = "장소", example = "본관 2층 대강당", nullable = true, maxLength = 500)
    val location: String?,
    @param:Schema(description = "프로그램 유형", example = "SPECIAL_LECTURE")
    val programType: ProgramType,
    @param:Schema(description = "대상 학년 목록", example = "[2, 3]")
    val targetGrades: List<Int>,
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
    @param:Schema(description = "요청한 학생이 신청 가능한지 여부", example = "true")
    val canApply: Boolean,
    @param:Schema(description = "지원 가능 여부 판정 사유", example = "AVAILABLE")
    val eligibilityReason: ProgramApplicationEligibilityReason,
    @param:Schema(description = "지원 가능 여부 판정 사유의 사용자용 메시지", example = "신청할 수 있습니다.")
    val eligibilityMessage: String,
    @param:Schema(description = "현재 요청 학생이 수행할 수 있는 Action 목록", example = "[\"APPLY\"]")
    val availableActions: List<String>,
    @param:Schema(description = "빈자리 알림 구독 가능 여부. 구독 기능 미구현으로 항상 false", example = "false")
    val canSubscribeVacancy: Boolean,
    @param:Schema(description = "빈자리 알림 구독 여부. 구독 기능 미구현으로 항상 false", example = "false")
    val vacancySubscribed: Boolean,
    @param:Schema(description = "빈자리 알림 구독 상태. 구독 기능 미구현으로 항상 null", nullable = true)
    val vacancySubscriptionStatus: String?,
    @param:Schema(description = "프로그램 상태", example = "PUBLISHED")
    val status: ProgramStatus,
    @param:Schema(description = "첨부파일 목록. DRAFT 상태에서 조회 권한이 없으면 빈 목록")
    val files: List<ProgramFileResponse>,
)
