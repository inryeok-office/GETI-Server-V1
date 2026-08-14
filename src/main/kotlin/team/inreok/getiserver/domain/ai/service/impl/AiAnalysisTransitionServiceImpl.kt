package team.inreok.getiserver.domain.ai.service.impl

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.ai.entity.JobAiAnalysis
import team.inreok.getiserver.domain.ai.entity.type.AiStatus
import team.inreok.getiserver.domain.ai.exception.AiAlreadyProcessingException
import team.inreok.getiserver.domain.ai.exception.AiReanalysisLimitExceededException
import team.inreok.getiserver.domain.ai.provider.AiAnalysisOutput
import team.inreok.getiserver.domain.ai.repository.JobAiAnalysisRepository
import team.inreok.getiserver.domain.ai.service.AiAnalysisTransitionService
import team.inreok.getiserver.domain.ai.service.AiReanalysisPreparation
import team.inreok.getiserver.domain.ai.service.MAX_REANALYSIS_COUNT
import team.inreok.getiserver.domain.ai.service.remainingReanalysisCount
import java.time.LocalDateTime

@Service
class AiAnalysisTransitionServiceImpl(
    private val jobAiAnalysisRepository: JobAiAnalysisRepository,
) : AiAnalysisTransitionService {
    // 같은 Job에 대해 JobChangedEvent가 짧은 간격으로 겹쳐 들어와 동시에 이 지점에 도달하면
    // 공유 PK(job_id) 충돌이 난다. 이미 다른 요청이 만든 것이므로 실패가 아니라 "이미 있었음"으로
    // 취급하고, 원본 예외는 의도적으로 무시한다(JwtTokenProvider.parseOrNull과 같은 방식).
    @Suppress("SwallowedException")
    @Transactional
    override fun createPendingIfAbsent(jobId: Long): Boolean {
        if (jobAiAnalysisRepository.existsById(jobId)) return false
        return try {
            // saveAndFlush로 즉시 반영해 공유 PK 충돌을 이 Transaction 안에서 곧바로 잡는다.
            jobAiAnalysisRepository.saveAndFlush(JobAiAnalysis(jobId = jobId, requestedAt = LocalDateTime.now()))
            true
        } catch (ex: DataIntegrityViolationException) {
            false
        }
    }

    @Transactional
    override fun prepareReanalysis(jobId: Long): AiReanalysisPreparation {
        val existing = jobAiAnalysisRepository.findByIdForUpdate(jobId)
        val now = LocalDateTime.now()

        if (existing == null) {
            // 최초 Trigger(JobChangedEvent) 처리가 아직 안 됐거나 실패했던 경우다. 이번 요청을
            // 최초 분석으로 취급하고 재분석 횟수를 소비하지 않는다.
            jobAiAnalysisRepository.save(
                JobAiAnalysis(jobId = jobId, requestedAt = now, status = AiStatus.PENDING),
            )
            return AiReanalysisPreparation(
                status = AiStatus.PENDING,
                isReanalysis = false,
                reanalysisCount = 0,
                remainingReanalysisCount = remainingReanalysisCount(0),
                canReanalyze = true,
                requestedAt = now,
            )
        }

        // PENDING도 이미 대기열에 있는 상태라 "처리 중"으로 취급한다 -- 그렇지 않으면 Worker가
        // 아직 집어가지 않은 짧은 틈에 같은 Job이 중복으로 대기열에 쌓이고 재분석 횟수도
        // 중복 소비된다.
        if (existing.status == AiStatus.PROCESSING || existing.status == AiStatus.PENDING) {
            throw AiAlreadyProcessingException(jobId)
        }
        if (existing.reanalysisCount >= MAX_REANALYSIS_COUNT) {
            throw AiReanalysisLimitExceededException(jobId, MAX_REANALYSIS_COUNT)
        }

        existing.reanalysisCount += 1
        existing.status = AiStatus.PENDING
        existing.requestedAt = now
        existing.errorMessage = null
        jobAiAnalysisRepository.save(existing)

        return AiReanalysisPreparation(
            status = AiStatus.PENDING,
            isReanalysis = true,
            reanalysisCount = existing.reanalysisCount,
            remainingReanalysisCount = remainingReanalysisCount(existing.reanalysisCount),
            canReanalyze = existing.reanalysisCount < MAX_REANALYSIS_COUNT,
            requestedAt = now,
        )
    }

    @Suppress("ReturnCount")
    @Transactional
    override fun claimForProcessing(jobId: Long): Boolean {
        val analysis = jobAiAnalysisRepository.findByIdForUpdate(jobId) ?: return false
        if (analysis.status != AiStatus.PENDING) return false
        analysis.status = AiStatus.PROCESSING
        jobAiAnalysisRepository.save(analysis)
        return true
    }

    @Transactional
    override fun markCompleted(
        jobId: Long,
        output: AiAnalysisOutput,
        requiredSkillsJson: String,
        preferredSkillsJson: String,
        promptVersion: String,
        analysisVersion: Int,
    ) {
        val analysis = jobAiAnalysisRepository.findByIdForUpdate(jobId) ?: return
        analysis.status = AiStatus.COMPLETED
        analysis.summary = output.summary
        analysis.requiredSkills = requiredSkillsJson
        analysis.preferredSkills = preferredSkillsJson
        analysis.highSchoolGraduateFit = output.highSchoolGraduateFit
        analysis.entryLevelFit = output.entryLevelFit
        analysis.difficulty = output.difficulty
        analysis.provider = "OPENAI"
        analysis.model = output.model
        analysis.promptVersion = promptVersion
        analysis.analysisVersion = analysisVersion
        analysis.errorMessage = null
        analysis.completedAt = LocalDateTime.now()
        jobAiAnalysisRepository.save(analysis)
    }

    @Transactional
    override fun markFailed(
        jobId: Long,
        sanitizedMessage: String,
    ) {
        val analysis = jobAiAnalysisRepository.findByIdForUpdate(jobId) ?: return
        analysis.status = AiStatus.FAILED
        analysis.errorMessage = sanitizedMessage
        analysis.completedAt = LocalDateTime.now()
        jobAiAnalysisRepository.save(analysis)
    }
}
