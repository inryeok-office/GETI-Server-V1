package team.inreok.getiserver.domain.program.service

import team.inreok.getiserver.domain.program.exception.DiscordChannelRequiredException
import team.inreok.getiserver.domain.program.exception.InvalidCapacityException
import team.inreok.getiserver.domain.program.exception.InvalidProgramPeriodException
import team.inreok.getiserver.domain.program.exception.InvalidTargetGradeException
import team.inreok.getiserver.domain.program.exception.ProgramValidationFailedException
import team.inreok.getiserver.domain.program.exception.TargetGradeRequiredException
import java.time.LocalDateTime

internal fun escapeLikePattern(value: String): String =
    value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

// 프로그램 값 검증 규칙이다(원본 요구사항 문서 6절). Job 도메인(JobValidation.kt)과 같은 원칙을
// 따른다 — 상태와 무관하게 항상 지켜야 하는 값 "형식"은 [validateProgramCommon]이 검증하고,
// 게시(PUBLISHED)에만 필요한 값 "존재"는 [validateProgramForPublish]가 검증한다.
//
// 원본 문서 6절의 "필수 검증" 목록이 등록(DRAFT 포함) 전체에 적용되는지, 게시 시점에만
// 적용되는지 명시하지 않아(Update 요청과 달리 Create 요청 JSON에 "(선택)" 표기가 없음),
// "등록·임시저장" API라는 제목과 Job 도메인의 기존 관례(DRAFT는 값이 비어 있어도 저장되고
// PUBLISHED만 필수값을 모두 검증)를 따라 게시 시점에만 필수로 판단했다 — 완료 보고에
// DECISION_REQUIRED로 남긴다.

// title 공백 검증은 ProgramCreateRequest의 @field:NotBlank(Bean Validation)가 담당한다(원본
// 문서 6절에 전용 ErrorCode가 없어 CommonErrorCode.VALIDATION_FAILED를 그대로 사용, Job
// 도메인이 별도 Domain Error Code를 쓰는 것과의 차이는 완료 보고에 남긴다).
@Suppress("LongParameterList")
internal fun validateProgramCommon(
    startAt: LocalDateTime?,
    endAt: LocalDateTime?,
    applicationStartAt: LocalDateTime?,
    applicationEndAt: LocalDateTime?,
    targetGrades: List<Int>,
    capacity: Int?,
) {
    validatePeriod(startAt, endAt, "행사 시작 시각은 종료 시각보다 늦을 수 없습니다.")
    validatePeriod(applicationStartAt, applicationEndAt, "신청 시작 시각은 종료 시각보다 늦을 수 없습니다.")
    validateTargetGrades(targetGrades)
    validateCapacity(capacity)
}

/**
 * 게시(PUBLISHED)에만 필요한 값이 모두 채워졌는지 검증한다. `targetGrades`는 Program 값이 아니라
 * 별도 Table(`program_target_grades`)로 관리해 Program Entity에 없으므로 Parameter로 받는다.
 */
@Suppress("LongParameterList", "ThrowsCount")
internal fun validateProgramForPublish(
    content: String?,
    location: String?,
    startAt: LocalDateTime?,
    endAt: LocalDateTime?,
    applicationStartAt: LocalDateTime?,
    applicationEndAt: LocalDateTime?,
    targetGrades: List<Int>,
    discordChannelId: String?,
) {
    if (content.isNullOrBlank()) throw ProgramValidationFailedException("게시하려면 프로그램 본문을 입력해야 합니다.")
    if (location.isNullOrBlank()) throw ProgramValidationFailedException("게시하려면 장소를 입력해야 합니다.")
    if (startAt == null || endAt == null) {
        throw InvalidProgramPeriodException("게시하려면 행사 시작·종료 일시가 모두 필요합니다.")
    }
    if (applicationStartAt == null || applicationEndAt == null) {
        throw InvalidProgramPeriodException("게시하려면 신청 시작·종료 일시가 모두 필요합니다.")
    }
    if (targetGrades.isEmpty()) throw TargetGradeRequiredException()
    if (discordChannelId.isNullOrBlank()) throw DiscordChannelRequiredException()
}

private fun validatePeriod(
    start: LocalDateTime?,
    end: LocalDateTime?,
    message: String,
) {
    if (start != null && end != null && start.isAfter(end)) throw InvalidProgramPeriodException(message)
}

private fun validateTargetGrades(targetGrades: List<Int>) {
    if (targetGrades.any { it !in MIN_TARGET_GRADE..MAX_TARGET_GRADE }) {
        throw InvalidTargetGradeException("대상 학년은 1, 2, 3 중 하나여야 합니다.")
    }
    if (targetGrades.size != targetGrades.toSet().size) {
        throw InvalidTargetGradeException("대상 학년은 중복될 수 없습니다.")
    }
}

private fun validateCapacity(capacity: Int?) {
    if (capacity != null && capacity <= 0) throw InvalidCapacityException()
}

private const val MIN_TARGET_GRADE = 1
private const val MAX_TARGET_GRADE = 3
