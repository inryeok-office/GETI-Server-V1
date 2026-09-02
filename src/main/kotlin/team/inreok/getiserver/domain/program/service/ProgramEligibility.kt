package team.inreok.getiserver.domain.program.service

import team.inreok.getiserver.domain.member.query.MemberApplicantSnapshot
import team.inreok.getiserver.domain.program.entity.Program
import team.inreok.getiserver.domain.program.entity.type.ProgramApplicationEligibilityReason
import team.inreok.getiserver.domain.program.entity.type.ProgramApplicationEligibilityReason.ALREADY_APPLIED
import team.inreok.getiserver.domain.program.entity.type.ProgramApplicationEligibilityReason.AVAILABLE
import team.inreok.getiserver.domain.program.entity.type.ProgramApplicationEligibilityReason.NOT_ENROLLED
import team.inreok.getiserver.domain.program.entity.type.ProgramApplicationEligibilityReason.NOT_TARGET_GRADE
import team.inreok.getiserver.domain.program.entity.type.ProgramApplicationEligibilityReason.PROGRAM_CLOSED
import team.inreok.getiserver.domain.program.entity.type.ProgramApplicationEligibilityReason.PROGRAM_FULL
import team.inreok.getiserver.domain.program.entity.type.ProgramApplicationEligibilityReason.PROGRAM_NOT_OPEN
import team.inreok.getiserver.domain.program.entity.type.ProgramApplicationEligibilityReason.PROGRAM_NOT_PUBLISHED
import team.inreok.getiserver.domain.program.entity.type.ProgramStatus
import java.time.LocalDateTime

// 재학 여부 판정에 쓰는 값이다(MemberApplicantSnapshot.academicStatus는 AcademicStatus.name
// 문자열, 요구사항 3절 "졸업은 Role이 아니라 학적 상태").
private const val ENROLLED_STATUS = "ENROLLED"

/**
 * 학생 Program 신청 가능 여부 판정이다(원본 요구사항 문서 10절의 순서를 그대로 따른다:
 * Program 존재/삭제 여부는 호출부가 먼저 걸러내고, 이 함수는 그다음 단계인 게시 여부부터
 * 정상 신청 가능까지를 담당한다). Repository/Port 호출이 없는 순수 함수로 둬 단위 Test에서
 * 모든 분기를 직접 검증할 수 있게 한다(JobApplicationEligibility.kt와 동일한 방식).
 *
 * `status == CLOSED`를 `status != PUBLISHED`(-> [PROGRAM_NOT_PUBLISHED])보다 먼저 명시적으로
 * 처리한다 -- `ProgramCloseScheduler`가 신청 종료 시각이 지난 PUBLISHED Program을 실제로
 * CLOSED로 전이하기 시작해(Phase 7) `status=CLOSED`인 Program이 이제 존재할 수 있고, 그 경우
 * 오분류하지 않고 [PROGRAM_CLOSED]를 반환해야 한다(PR #81 리뷰 MINOR 지적, 회귀 테스트:
 * ProgramEligibilityTest 하단 참고).
 */
@Suppress("ReturnCount", "LongParameterList")
fun computeProgramEligibilityReason(
    program: Program,
    targetGrades: Set<Int>,
    member: MemberApplicantSnapshot?,
    hasActiveApplication: Boolean,
    currentApplicants: Int,
    now: LocalDateTime,
): ProgramApplicationEligibilityReason {
    if (program.status == ProgramStatus.CLOSED) return PROGRAM_CLOSED
    if (program.status != ProgramStatus.PUBLISHED) return PROGRAM_NOT_PUBLISHED
    if (member == null || member.academicStatus != ENROLLED_STATUS) return NOT_ENROLLED
    if (targetGrades.isNotEmpty() && member.grade !in targetGrades) return NOT_TARGET_GRADE
    val applicationStartedAt = program.applicationStartedAt
    if (applicationStartedAt != null && now.isBefore(applicationStartedAt)) return PROGRAM_NOT_OPEN
    val applicationEndedAt = program.applicationEndedAt
    if (applicationEndedAt != null && now.isAfter(applicationEndedAt)) return PROGRAM_CLOSED
    if (hasActiveApplication) return ALREADY_APPLIED
    val capacity = program.capacity
    if (capacity != null && currentApplicants >= capacity) return PROGRAM_FULL
    return AVAILABLE
}

fun programEligibilityMessageOf(reason: ProgramApplicationEligibilityReason): String =
    when (reason) {
        AVAILABLE -> "신청할 수 있습니다."
        NOT_ENROLLED -> "재학 중인 학생만 신청할 수 있습니다."
        NOT_TARGET_GRADE -> "신청 대상 학년이 아닙니다."
        PROGRAM_NOT_OPEN -> "아직 신청 기간이 아닙니다."
        PROGRAM_CLOSED -> "신청이 마감되었습니다."
        PROGRAM_FULL -> "정원이 마감되었습니다."
        ALREADY_APPLIED -> "이미 이 프로그램에 신청한 이력이 있습니다."
        PROGRAM_NOT_PUBLISHED -> "현재 신청할 수 없는 프로그램입니다."
    }
