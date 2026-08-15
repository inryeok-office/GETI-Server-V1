package team.inreok.getiserver.domain.ai.service

/**
 * `job_ai_analyses.required_skills`/`preferred_skills`(JSONB)에 저장하는 내부 표현이다. 다른
 * Module에는 공개하지 않는다 -- Job 상세 응답에 노출할 때는 `job.access.JobAiSkillAccessView`로
 * 다시 옮겨 담는다(`JobAiAnalysisAccessorImpl` 참고).
 */
data class AiSkill(
    val techStackId: Long?,
    val name: String,
)
