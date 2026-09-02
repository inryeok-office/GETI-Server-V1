package team.inreok.getiserver.domain.job.query

import org.springframework.modulith.NamedInterface
import java.time.LocalDateTime

/**
 * `ai` Module이 AI 분석(OpenAI Prompt 입력)에 필요한 공고 정보를 읽는 공개 계약이다(AI Analysis
 * Phase 1, Issue #132). `ai`는 이 Interface를 통해서만 Job을 읽고, `Job` Entity나
 * `JobRepository`를 직접 참조하지 않는다.
 *
 * `[JobDiscordPayloadQueryPort]`와 같은 이유로 이 계약도 `notification`이 아니라 `job`이 아니라
 * `ai`가 아니라 `job`에 있다 -- 반대 방향으로 두면 `job`이 `ai.event`(향후 생길 경우)를 구독할 때
 * 순환 의존이 생긴다.
 *
 * 실제 Job/Company에 존재하는 값만 담는다. `deletedAt`, 작성자 개인정보, Discord 채널,
 * `applicationFormSchema` 등 분석에 불필요한 내부 값은 포함하지 않는다(GETI Notion AI Analysis
 * Phase 1 "Input 데이터" 절).
 */
@NamedInterface
interface JobAiAnalysisInputQueryPort {
    /** 존재하지 않거나 삭제된 공고면 null을 반환한다. `status`로 PUBLISHED 여부는 호출 측이 판단한다. */
    fun findById(jobId: Long): JobAiAnalysisInputSnapshot?
}

@NamedInterface
data class JobAiAnalysisInputSnapshot(
    val jobId: Long,
    val title: String,
    /** `PostingType.name` */
    val postingType: String,
    /** `ApplicationMethod.name` */
    val applicationMethod: String,
    /** `JobStatus.name`. AI 분석은 PUBLISHED에만 적용한다 -- 호출 측이 이 값으로 판단한다. */
    val status: String,
    /** 삭제된 기업이면 null이다. */
    val companyName: String?,
    val content: String?,
    val startDate: LocalDateTime?,
    val endDate: LocalDateTime?,
    val targetGrade: Int?,
)
