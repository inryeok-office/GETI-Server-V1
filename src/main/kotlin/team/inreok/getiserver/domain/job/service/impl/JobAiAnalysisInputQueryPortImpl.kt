package team.inreok.getiserver.domain.job.service.impl

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.company.query.CompanyQuery
import team.inreok.getiserver.domain.job.query.JobAiAnalysisInputQueryPort
import team.inreok.getiserver.domain.job.query.JobAiAnalysisInputSnapshot
import team.inreok.getiserver.domain.job.repository.JobRepository

/**
 * 다른 Domain Module(ai)에 공개된 조회 계약([JobAiAnalysisInputQueryPort])의 구현이다.
 * [JobDiscordPayloadQueryPortImpl]과 같은 이유로 `JobServiceImpl`에 합치지 않고 분리한다.
 */
@Service
class JobAiAnalysisInputQueryPortImpl(
    private val jobRepository: JobRepository,
    private val companyQuery: CompanyQuery,
) : JobAiAnalysisInputQueryPort {
    @Transactional(readOnly = true)
    override fun findById(jobId: Long): JobAiAnalysisInputSnapshot? {
        val job = jobRepository.findByIdAndDeletedAtIsNull(jobId) ?: return null
        return JobAiAnalysisInputSnapshot(
            jobId = requireNotNull(job.id) { "저장된 Job은 id를 가져야 합니다." },
            title = job.title,
            postingType = job.type.name,
            applicationMethod = job.applicationMethod.name,
            status = job.status.name,
            companyName = companyQuery.findActiveSummary(job.companyId)?.name,
            content = job.bodyMarkdown,
            startDate = job.recruitmentStartedAt,
            endDate = job.recruitmentEndedAt,
            targetGrade = job.targetGrade,
        )
    }
}
