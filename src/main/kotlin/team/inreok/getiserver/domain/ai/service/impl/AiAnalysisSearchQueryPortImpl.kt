package team.inreok.getiserver.domain.ai.service.impl

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.ai.entity.JobAiAnalysis
import team.inreok.getiserver.domain.ai.entity.type.AiStatus
import team.inreok.getiserver.domain.ai.query.AiAnalysisSearchQueryPort
import team.inreok.getiserver.domain.ai.query.AiAnalysisSearchSnapshot
import team.inreok.getiserver.domain.ai.repository.JobAiAnalysisRepository
import team.inreok.getiserver.domain.ai.service.AiSkill
import tools.jackson.databind.ObjectMapper

/**
 * [AiAnalysisSearchQueryPort]의 구현이다(Issue #144). `requiredSkills`/`preferredSkills` JSON을
 * 파싱하는 방식은 `JobAiAnalysisAccessorImpl.parseSkills`와 같다.
 */
@Service
class AiAnalysisSearchQueryPortImpl(
    private val jobAiAnalysisRepository: JobAiAnalysisRepository,
    private val objectMapper: ObjectMapper,
) : AiAnalysisSearchQueryPort {
    @Transactional(readOnly = true)
    override fun findCompletedByJobIds(jobIds: Collection<Long>): Map<Long, AiAnalysisSearchSnapshot> {
        if (jobIds.isEmpty()) return emptyMap()
        return jobAiAnalysisRepository
            .findAllById(jobIds)
            .filter { it.status == AiStatus.COMPLETED }
            .associate { it.jobId to toSnapshot(it) }
    }

    private fun toSnapshot(analysis: JobAiAnalysis): AiAnalysisSearchSnapshot =
        AiAnalysisSearchSnapshot(
            requiredTechStackIds = parseTechStackIds(analysis.requiredSkills),
            preferredTechStackIds = parseTechStackIds(analysis.preferredSkills),
            highSchoolGraduateFit = analysis.highSchoolGraduateFit?.name,
            entryLevelFit = analysis.entryLevelFit?.name,
            difficulty = analysis.difficulty?.name,
        )

    /** GETI Tech Stack Master와 매칭되지 않은 기술(techStackId = null)은 제외한다. */
    private fun parseTechStackIds(json: String?): List<Long> {
        if (json.isNullOrBlank()) return emptyList()
        return objectMapper.readValue(json, Array<AiSkill>::class.java).mapNotNull { it.techStackId }
    }
}
