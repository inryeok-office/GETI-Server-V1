package team.inreok.getiserver.domain.program.entity.type

// 학생별 Program 지원 가능 여부 판정 사유다(요구사항 4절/10절). Job Application의
// JobApplicationEligibilityReason과 값 구성이 다르고(NOT_TARGET_GRADE/PROGRAM_FULL 등 Program
// 고유 사유 포함) 요구사항이 재사용을 명시적으로 금지해 별도로 정의한다.
enum class ProgramApplicationEligibilityReason {
    AVAILABLE,
    NOT_ENROLLED,
    NOT_TARGET_GRADE,
    PROGRAM_NOT_OPEN,
    PROGRAM_CLOSED,
    PROGRAM_FULL,
    ALREADY_APPLIED,
    PROGRAM_NOT_PUBLISHED,
}
