package team.inreok.getiserver.domain.job.access

import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.modulith.NamedInterface
import java.time.LocalDateTime

/**
 * `job`이 소유하고 `ai`가 구현하는 SPI다(`FileAccessChecker`와 같은 방식 --
 * `docs/architecture/modularity.md` "Domain 간 허용 의존" 참고). AI 분석 결과를 Job 상세 응답
 * (`aiAnalysis`)에 포함하기 위해 필요하다.
 *
 * 방향을 이렇게 둔 이유: `ai`는 이미 `job.event.JobChangedEvent`(분석 Trigger)와
 * `job.query.JobAiAnalysisInputQueryPort`(Prompt 입력)를 통해 `job`에 의존한다. 만약 `job`이
 * 거꾸로 `ai.query`에 의존하면 `ai -> job -> ai` 순환 의존이 생겨 `ModularityTest`
 * (`modules.verify()`)가 실패한다(`JobController`의 Class 주석에 있는 `job`/`search` 사례와
 * 같은 문제). 대신 `job`이 이 Interface(자신이 소유한 타입)를 정의하고, `ai`가 구현체를 Bean으로
 * 등록한다 -- `job`은 `ai`의 어떤 Package도 참조하지 않는다.
 */
@NamedInterface
interface JobAiAnalysisAccessor {
    /** 아직 분석이 시작되지 않았으면(예: PUBLISHED 이후 비동기 Trigger가 아직 처리되지 않음) null. */
    fun findSnapshot(jobId: Long): JobAiAnalysisAccessSnapshot?

    /**
     * Job Summary의 `techStacks` Field(Issue #196)를 위한 Batch 조회다. [jobIds] 각각의
     * `requiredSkills`/`preferredSkills`를 합쳐 GETI 기술스택 마스터와 매칭된 것(`techStackId`가
     * 있는 것)만 `techStackId` 기준으로 중복 제거해 돌려준다. 매칭되지 않은 기술 이름(`techStackId`
     * null)은 포함하지 않는다 -- AI가 자유롭게 추출한 이름을 그대로 노출하면 GETI 기술스택 마스터에
     * 없는 값이 섞여 Client가 이후 필터·검색에 활용하기 어렵다. 목록 항목마다 [findSnapshot]을
     * 반복 호출하면 N+1이 발생하므로 별도 Batch Method로 둔다. 분석이 없거나 매칭된 기술이 없는
     * jobId는 결과 Map에 없거나 빈 목록이다.
     */
    fun findMatchedTechStacks(jobIds: Set<Long>): Map<Long, List<JobAiSkillAccessView>>
}

/**
 * `CompanySummary`(`domain.company.query`)와 같은 방식으로 Swagger Annotation을 직접 붙여
 * `JobDetailResponse.aiAnalysis`에 그대로 재사용한다. `status`/`highSchoolGraduateFit`/
 * `entryLevelFit`/`difficulty`는 `ai.entity.type`의 Enum 이름 문자열이다 -- `job`이 `ai`의 Enum
 * 타입을 직접 참조하면 다시 순환 의존이 생기므로 String으로 노출한다.
 */
@NamedInterface
@Schema(description = "공고 상세에 포함되는 AI 분석 결과. AI 분석 결과는 참고용이다.")
data class JobAiAnalysisAccessSnapshot(
    @param:Schema(
        description = "분석 상태",
        example = "COMPLETED",
        allowableValues = ["PENDING", "PROCESSING", "COMPLETED", "FAILED"],
    )
    val status: String,
    @param:Schema(description = "이번 분석이 사용자 요청에 의한 재분석인지 여부", example = "false")
    val isReanalysis: Boolean,
    @param:Schema(description = "핵심 요약(2~4문장). 분석이 끝나지 않았으면 null.", nullable = true)
    val summary: String?,
    @param:Schema(description = "필수 기술 및 도구. 분석이 끝나지 않았으면 빈 목록.")
    val requiredSkills: List<JobAiSkillAccessView>,
    @param:Schema(description = "우대 기술 및 경험. 분석이 끝나지 않았으면 빈 목록.")
    val preferredSkills: List<JobAiSkillAccessView>,
    @param:Schema(
        description = "고졸 지원 적합성. 분석이 끝나지 않았으면 null.",
        example = "SUITABLE",
        nullable = true,
        allowableValues = ["SUITABLE", "CONDITIONAL", "UNSUITABLE"],
    )
    val highSchoolGraduateFit: String?,
    @param:Schema(
        description = "신입 지원 적합성. 분석이 끝나지 않았으면 null.",
        example = "SUITABLE",
        nullable = true,
        allowableValues = ["SUITABLE", "CONDITIONAL", "UNSUITABLE"],
    )
    val entryLevelFit: String?,
    @param:Schema(
        description = "공고 진입 난이도. 분석이 끝나지 않았으면 null.",
        example = "NORMAL",
        nullable = true,
        allowableValues = ["EASY", "NORMAL", "HARD"],
    )
    val difficulty: String?,
    @param:Schema(description = "지금 재분석을 요청할 수 있는지(PROCESSING이 아니고 3회 미만)", example = "true")
    val canReanalyze: Boolean,
    @param:Schema(description = "남은 수동 재분석 가능 횟수(0~3)", example = "3")
    val remainingReanalysisCount: Int,
    @param:Schema(description = "마지막으로 분석이 완료된 시각. COMPLETED가 아니면 null.", nullable = true)
    val analyzedAt: LocalDateTime?,
)

@NamedInterface
@Schema(description = "AI가 추출한 기술. GETI 기술스택 마스터와 정확히 매칭되면 techStackId가 채워진다.")
data class JobAiSkillAccessView(
    @param:Schema(description = "매칭된 GETI 기술스택 ID. 매칭되지 않으면 null.", example = "1", nullable = true)
    val techStackId: Long?,
    @param:Schema(description = "AI가 추출한 기술 이름", example = "Spring Boot")
    val name: String,
)
