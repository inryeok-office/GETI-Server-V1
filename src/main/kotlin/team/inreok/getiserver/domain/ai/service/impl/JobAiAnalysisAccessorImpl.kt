package team.inreok.getiserver.domain.ai.service.impl

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.ai.entity.type.AiStatus
import team.inreok.getiserver.domain.ai.repository.JobAiAnalysisRepository
import team.inreok.getiserver.domain.ai.service.AiSkill
import team.inreok.getiserver.domain.ai.service.canReanalyze
import team.inreok.getiserver.domain.ai.service.remainingReanalysisCount
import team.inreok.getiserver.domain.job.access.JobAiAnalysisAccessSnapshot
import team.inreok.getiserver.domain.job.access.JobAiAnalysisAccessor
import team.inreok.getiserver.domain.job.access.JobAiSkillAccessView
import tools.jackson.databind.ObjectMapper

/**
 * `job`이 정의한 SPI([JobAiAnalysisAccessor])의 구현이다. `job -> ai` 순환 의존을 만들지 않기
 * 위해 `ai`가 `job`의 Interface를 구현하는 방향을 쓴다(`JobAiAnalysisAccessor`의 Class 주석 참고).
 */
@Service
class JobAiAnalysisAccessorImpl(
    private val jobAiAnalysisRepository: JobAiAnalysisRepository,
    private val objectMapper: ObjectMapper,
) : JobAiAnalysisAccessor {
    @Transactional(readOnly = true)
    override fun findSnapshot(jobId: Long): JobAiAnalysisAccessSnapshot? {
        val analysis = jobAiAnalysisRepository.findById(jobId).orElse(null) ?: return null
        return JobAiAnalysisAccessSnapshot(
            status = analysis.status.name,
            isReanalysis = analysis.reanalysisCount > 0,
            summary = analysis.summary,
            requiredSkills = parseSkills(analysis.requiredSkills),
            preferredSkills = parseSkills(analysis.preferredSkills),
            highSchoolGraduateFit = analysis.highSchoolGraduateFit?.name,
            entryLevelFit = analysis.entryLevelFit?.name,
            difficulty = analysis.difficulty?.name,
            canReanalyze = canReanalyze(analysis.status, analysis.reanalysisCount),
            remainingReanalysisCount = remainingReanalysisCount(analysis.reanalysisCount),
            analyzedAt = if (analysis.status == AiStatus.COMPLETED) analysis.completedAt else null,
        )
    }

    private fun parseSkills(json: String?): List<JobAiSkillAccessView> {
        if (json.isNullOrBlank()) return emptyList()
        return objectMapper
            .readValue(json, Array<AiSkill>::class.java)
            .map { JobAiSkillAccessView(techStackId = it.techStackId, name = it.name) }
    }
}
