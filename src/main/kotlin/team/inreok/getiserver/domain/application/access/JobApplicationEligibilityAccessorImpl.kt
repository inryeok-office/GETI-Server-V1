package team.inreok.getiserver.domain.application.access

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.application.entity.type.FormStatus
import team.inreok.getiserver.domain.application.entity.type.JobApplicationEligibilityReason
import team.inreok.getiserver.domain.application.repository.FormRepository
import team.inreok.getiserver.domain.application.repository.JobApplicationFormRepository
import team.inreok.getiserver.domain.application.repository.JobApplicationRepository
import team.inreok.getiserver.domain.application.service.impl.ACTIVE_JOB_APPLICATION_STATUSES
import team.inreok.getiserver.domain.application.service.impl.availableStudentActionNames
import team.inreok.getiserver.domain.application.service.impl.computeEligibilityReason
import team.inreok.getiserver.domain.application.service.impl.eligibilityMessageOf
import team.inreok.getiserver.domain.job.access.JobApplicationEligibilityAccessSnapshot
import team.inreok.getiserver.domain.job.access.JobApplicationEligibilityAccessor
import team.inreok.getiserver.domain.job.query.JobApplicationSnapshotQueryPort
import team.inreok.getiserver.domain.member.query.MemberApplicantSnapshotQueryPort
import java.time.LocalDateTime

/**
 * `job.access.JobApplicationEligibilityAccessor`(SPI)의 구현이다(Issue #136). 학생 지원 가능
 * 여부 판정 자체는 새로 만들지 않고 Phase 2가 이미 구현한 [computeEligibilityReason]/
 * [eligibilityMessageOf](`JobApplicationEligibility.kt`)와 Phase 3가 만든
 * [availableStudentActionNames](`JobApplicationServiceImpl.kt`)를 그대로 재사용한다 --
 * `JobApplicationServiceImpl.checkEligibility`와 같은 계산을 배치로 반복한다.
 *
 * `job`/`search`가 이 Interface를 소유하지 않고 `application`이 구현하는 이유는
 * [JobApplicationEligibilityAccessor]의 Class 주석 참고.
 */
@Component
class JobApplicationEligibilityAccessorImpl(
    private val jobApplicationSnapshotQueryPort: JobApplicationSnapshotQueryPort,
    private val memberApplicantSnapshotQueryPort: MemberApplicantSnapshotQueryPort,
    private val jobApplicationFormRepository: JobApplicationFormRepository,
    private val formRepository: FormRepository,
    private val jobApplicationRepository: JobApplicationRepository,
) : JobApplicationEligibilityAccessor {
    // 여러 Repository/Port를 순서대로 읽는 조회이므로 checkEligibility/
    // JobApplicationSnapshotQueryPortImpl과 동일하게 하나의 읽기 전용 Transaction으로 묶는다.
    @Transactional(readOnly = true)
    override fun findAllByJobIds(
        jobIds: Set<Long>,
        requesterMemberId: Long,
    ): Map<Long, JobApplicationEligibilityAccessSnapshot> {
        if (jobIds.isEmpty()) return emptyMap()

        val jobs = jobApplicationSnapshotQueryPort.findAllByIds(jobIds)
        val member = memberApplicantSnapshotQueryPort.findById(requesterMemberId)
        val activeLinkedFormByJobId = activeLinkedFormByJobId(jobIds)
        val activeApplicationByJobId =
            jobApplicationRepository
                .findByJobIdInAndApplicantMemberIdAndStatusIn(
                    jobIds,
                    requesterMemberId,
                    ACTIVE_JOB_APPLICATION_STATUSES,
                ).associateBy { it.jobId }
        val now = LocalDateTime.now()

        return jobIds.associateWith { jobId ->
            val activeApplication = activeApplicationByJobId[jobId]
            val reason =
                computeEligibilityReason(
                    job = jobs[jobId],
                    member = member,
                    hasActiveLinkedForm = activeLinkedFormByJobId[jobId] ?: false,
                    hasActiveApplication = activeApplication != null,
                    now = now,
                )
            JobApplicationEligibilityAccessSnapshot(
                canApply = reason == JobApplicationEligibilityReason.AVAILABLE,
                eligibilityReason = reason.name,
                eligibilityMessage = eligibilityMessageOf(reason),
                applicationId = activeApplication?.id,
                applicationStatus = activeApplication?.status?.name,
                availableActions =
                    when {
                        activeApplication != null -> availableStudentActionNames(activeApplication.status)
                        reason == JobApplicationEligibilityReason.AVAILABLE -> listOf(CREATE_DRAFT_ACTION)
                        else -> emptyList()
                    },
            )
        }
    }

    /**
     * `JobApplicationEligibility.kt`의 `activeLinkedForm`(단건)과 같은 판정을 jobId Set 전체에
     * 대해 한 번에 계산한다(N+1 방지).
     */
    private fun activeLinkedFormByJobId(jobIds: Set<Long>): Map<Long, Boolean> {
        val links = jobApplicationFormRepository.findAllById(jobIds)
        if (links.isEmpty()) return emptyMap()
        val activeFormIds =
            formRepository
                .findAllById(links.map { it.formId }.toSet())
                .filter { it.status == FormStatus.ACTIVE }
                .mapNotNull { it.id }
                .toSet()
        return links.associate { it.jobId to (it.formId in activeFormIds) }
    }

    private companion object {
        const val CREATE_DRAFT_ACTION = "CREATE_DRAFT"
    }
}
