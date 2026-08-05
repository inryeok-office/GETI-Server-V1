package team.inreok.getiserver.domain.application.service.impl

import team.inreok.getiserver.domain.application.entity.type.JobApplicationEligibilityReason
import team.inreok.getiserver.domain.application.entity.type.JobApplicationEligibilityReason.AFTER_END
import team.inreok.getiserver.domain.application.entity.type.JobApplicationEligibilityReason.ALREADY_APPLIED
import team.inreok.getiserver.domain.application.entity.type.JobApplicationEligibilityReason.AVAILABLE
import team.inreok.getiserver.domain.application.entity.type.JobApplicationEligibilityReason.BEFORE_START
import team.inreok.getiserver.domain.application.entity.type.JobApplicationEligibilityReason.JOB_NOT_PUBLISHED
import team.inreok.getiserver.domain.application.entity.type.JobApplicationEligibilityReason.NOT_ENROLLED
import team.inreok.getiserver.domain.application.entity.type.JobApplicationEligibilityReason.NOT_INTERNAL
import team.inreok.getiserver.domain.application.entity.type.JobApplicationEligibilityReason.NOT_TARGET_GRADE
import team.inreok.getiserver.domain.application.entity.type.JobApplicationStatus
import team.inreok.getiserver.domain.job.query.JobApplicationJobSnapshot
import team.inreok.getiserver.domain.member.query.MemberApplicantSnapshot
import java.time.LocalDateTime

// ALREADY_APPLIED 판정(§6.5 9번)에 쓰는 "활성" 지원 상태 집합이다. WITHDRAWN/REJECTED는
// 활성이 아니라 재지원할 수 있다(요구사항 22절).
val ACTIVE_JOB_APPLICATION_STATUSES: Set<JobApplicationStatus> =
    setOf(
        JobApplicationStatus.DRAFT,
        JobApplicationStatus.SUBMITTED,
        JobApplicationStatus.EDIT_REQUESTED,
        JobApplicationStatus.EDIT_ALLOWED,
        JobApplicationStatus.REVISION_REQUESTED,
        JobApplicationStatus.APPROVED,
    )

/**
 * 학생 지원 가능 여부 판정이다(요구사항 7절). 순서와 각 판정 기준은
 * `docs/application/application-domain-plan.md` §6.5를 그대로 따른다. Repository/Port 호출이
 * 없는 순수 함수로 둬 단위 Test에서 모든 분기를 직접 검증할 수 있게 한다.
 */
@Suppress("ReturnCount")
fun computeEligibilityReason(
    job: JobApplicationJobSnapshot?,
    member: MemberApplicantSnapshot?,
    hasActiveLinkedForm: Boolean,
    hasActiveApplication: Boolean,
    now: LocalDateTime,
): JobApplicationEligibilityReason {
    if (job == null || job.status != "PUBLISHED") return JOB_NOT_PUBLISHED
    if (job.applicationMethod != "INTERNAL") return NOT_INTERNAL
    if (member == null || member.academicStatus != "ENROLLED") return NOT_ENROLLED
    if (job.targetGrade != null && job.targetGrade != member.grade) return NOT_TARGET_GRADE
    if (job.recruitmentStartedAt != null && now.isBefore(job.recruitmentStartedAt)) return BEFORE_START
    if (job.recruitmentEndedAt != null && now.isAfter(job.recruitmentEndedAt)) return AFTER_END
    if (!hasActiveLinkedForm) return JOB_NOT_PUBLISHED
    if (hasActiveApplication) return ALREADY_APPLIED
    return AVAILABLE
}

fun eligibilityMessageOf(reason: JobApplicationEligibilityReason): String =
    when (reason) {
        AVAILABLE -> "지원 가능한 공고입니다."
        NOT_INTERNAL -> "교내 지원 방식이 아닌 공고입니다."
        NOT_ENROLLED -> "재학 중인 학생만 지원할 수 있습니다."
        NOT_TARGET_GRADE -> "지원 대상 학년이 아닙니다."
        BEFORE_START -> "아직 모집 시작 전입니다."
        AFTER_END -> "모집이 마감되었습니다."
        ALREADY_APPLIED -> "이미 이 공고에 지원한 이력이 있습니다."
        JOB_NOT_PUBLISHED -> "현재 지원할 수 없는 공고입니다."
    }
