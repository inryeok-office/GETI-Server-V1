package team.inreok.getiserver.domain.ai.service.impl

import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import team.inreok.getiserver.domain.ai.dto.AiReanalysisResponse
import team.inreok.getiserver.domain.ai.event.AiAnalysisCompletedEvent
import team.inreok.getiserver.domain.ai.event.AiAnalysisRequestedEvent
import team.inreok.getiserver.domain.ai.exception.AiJobNotFoundException
import team.inreok.getiserver.domain.ai.exception.AiProviderUnavailableException
import team.inreok.getiserver.domain.ai.provider.AiAnalysisInput
import team.inreok.getiserver.domain.ai.provider.AiAnalysisProvider
import team.inreok.getiserver.domain.ai.provider.AiAnalysisProviderException
import team.inreok.getiserver.domain.ai.provider.openai.JOB_ANALYSIS_PROMPT_VERSION
import team.inreok.getiserver.domain.ai.provider.openai.JOB_ANALYSIS_VERSION
import team.inreok.getiserver.domain.ai.service.AiAnalysisService
import team.inreok.getiserver.domain.ai.service.AiAnalysisTransitionService
import team.inreok.getiserver.domain.ai.service.AiSkill
import team.inreok.getiserver.domain.job.query.JobAiAnalysisInputQueryPort
import team.inreok.getiserver.domain.job.query.JobAiAnalysisInputSnapshot
import team.inreok.getiserver.domain.member.query.TechStackMatchQueryPort
import tools.jackson.databind.ObjectMapper

/**
 * AI 분석 Use Case 전체를 조율한다. 실제 상태 전이(DB Write)는 [AiAnalysisTransitionService]에
 * 위임하고(Self-Invocation 문제 회피, 그 Class의 KDoc 참고), 이 Class는 Provider 호출·Tech
 * Stack 매칭 같은 I/O를 Transaction 밖에서 수행한다 -- OpenAI 호출을 DB Transaction 안에 두지
 * 않는다(`.claude/rules/spring-boot.md` Transaction Convention).
 */
@Service
class AiAnalysisServiceImpl(
    private val transitionService: AiAnalysisTransitionService,
    private val jobAiAnalysisInputQueryPort: JobAiAnalysisInputQueryPort,
    private val aiAnalysisProvider: AiAnalysisProvider,
    private val techStackMatchQueryPort: TechStackMatchQueryPort,
    private val objectMapper: ObjectMapper,
    private val eventPublisher: ApplicationEventPublisher,
) : AiAnalysisService {
    override fun triggerInitialAnalysisIfAbsent(jobId: Long) {
        if (transitionService.createPendingIfAbsent(jobId)) {
            eventPublisher.publishEvent(AiAnalysisRequestedEvent(jobId))
        }
    }

    override fun reanalyze(jobId: Long): AiReanalysisResponse {
        val input = jobAiAnalysisInputQueryPort.findById(jobId)
        if (input == null || input.status != JOB_STATUS_PUBLISHED) throw AiJobNotFoundException(jobId)
        // Provider가 설정되지 않았으면 결과가 100% FAILED로 끝난다는 것이 이미 확실하다. 그런데도
        // 접수를 진행하면 실패가 예정된 시도에 수동 재분석 횟수(최대 3회)만 소비된다 -- 이후 Key를
        // 정상적으로 설정해도 그 공고는 이미 소진된 횟수 때문에 재분석할 수 없다(Code Review
        // 지적 사항, Issue #132). 횟수를 건드리기 전에 즉시 거부한다.
        if (!aiAnalysisProvider.isConfigured()) throw AiProviderUnavailableException(jobId)

        val preparation = transitionService.prepareReanalysis(jobId)
        eventPublisher.publishEvent(AiAnalysisRequestedEvent(jobId))

        return AiReanalysisResponse(
            jobId = jobId,
            status = preparation.status,
            isReanalysis = preparation.isReanalysis,
            reanalysisCount = preparation.reanalysisCount,
            remainingReanalysisCount = preparation.remainingReanalysisCount,
            canReanalyze = preparation.canReanalyze,
            requestedAt = preparation.requestedAt,
        )
    }

    // claimForProcessing이 true를 반환한 뒤(PROCESSING으로 이미 Commit됨)부터는 전체를 하나의
    // try로 감싼다. Input 조회(jobAiAnalysisInputQueryPort.findById)를 try 밖에 두면 그 호출
    // 자체가 예외를 던졌을 때(DB 오류 등) FAILED로 되돌릴 방법이 없어 Row가 PROCESSING에
    // 영구 고착된다 -- 되돌릴 Scheduler가 없고 prepareReanalysis는 PROCESSING을 409로 거부하므로
    // 사용자도 운영자도 API로 복구할 수 없다(Code Review 지적 사항, Issue #132).
    //
    // finally에서 AiAnalysisCompletedEvent를 발행한다 -- markCompleted/markFailed 세 호출 지점
    // 모두(대상 없음/Provider 실패/예상 못한 오류) 분석 시도가 끝났다는 점은 같고, search가 이
    // Event로 현재 상태를 다시 조회하면 성공/실패 여부와 무관하게 항상 최신 값에 수렴한다
    // (AiAnalysisCompletedEvent KDoc 참고). claimForProcessing이 false를 반환해 즉시 return하는
    // 경로(중복 처리 방지)는 실제로 상태가 바뀌지 않았으므로 Event를 발행하지 않는다.
    @Suppress("TooGenericExceptionCaught")
    override fun processAnalysis(jobId: Long) {
        if (!transitionService.claimForProcessing(jobId)) return

        try {
            val input = jobAiAnalysisInputQueryPort.findById(jobId)
            if (input == null || input.status != JOB_STATUS_PUBLISHED) {
                transitionService.markFailed(jobId, "분석 대상 공고를 찾을 수 없거나 더 이상 게시 상태가 아닙니다.")
                return
            }

            val output = aiAnalysisProvider.analyze(toProviderInput(input))
            val requiredSkills = matchSkills(output.requiredSkillNames)
            val preferredSkills = matchSkills(output.preferredSkillNames)
            transitionService.markCompleted(
                jobId = jobId,
                output = output,
                requiredSkillsJson = objectMapper.writeValueAsString(requiredSkills),
                preferredSkillsJson = objectMapper.writeValueAsString(preferredSkills),
                promptVersion = JOB_ANALYSIS_PROMPT_VERSION,
                analysisVersion = JOB_ANALYSIS_VERSION,
            )
        } catch (ex: AiAnalysisProviderException) {
            log.warn("AI 분석 Provider 호출이 실패했습니다(jobId={}, code={}).", jobId, ex.code)
            transitionService.markFailed(jobId, ex.message ?: "AI 분석 호출에 실패했습니다.")
        } catch (ex: Exception) {
            log.error("AI 분석 처리 중 예상하지 못한 오류가 발생했습니다(jobId={}).", jobId, ex)
            transitionService.markFailed(jobId, "AI 분석 처리 중 오류가 발생했습니다.")
        } finally {
            eventPublisher.publishEvent(AiAnalysisCompletedEvent(jobId))
        }
    }

    /** 매칭되지 않은 이름도 [AiSkill.name]으로 보존한다(`techStackId = null`, GETI Notion 확정 계약). */
    private fun matchSkills(names: List<String>): List<AiSkill> {
        if (names.isEmpty()) return emptyList()
        val matched = techStackMatchQueryPort.matchByNames(names)
        return names.map { name -> AiSkill(techStackId = matched[name], name = name) }
    }

    private fun toProviderInput(input: JobAiAnalysisInputSnapshot): AiAnalysisInput =
        AiAnalysisInput(
            jobId = input.jobId,
            title = input.title,
            postingType = input.postingType,
            applicationMethod = input.applicationMethod,
            companyName = input.companyName,
            content = input.content,
            startDate = input.startDate,
            endDate = input.endDate,
            targetGrade = input.targetGrade,
        )

    private companion object {
        val log = LoggerFactory.getLogger(AiAnalysisServiceImpl::class.java)

        // job.entity.type.JobStatus는 Module 내부 구현이라 참조할 수 없다(job.query가 이미
        // String으로 노출한 이유이기도 하다). "PUBLISHED"는 JobStatus.PUBLISHED.name과 항상
        // 같아야 하는 문자열 계약이다.
        const val JOB_STATUS_PUBLISHED = "PUBLISHED"
    }
}
