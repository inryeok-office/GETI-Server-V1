package team.inreok.getiserver.domain.collector.eligibility

import team.inreok.getiserver.domain.collector.entity.type.JobSourceCode
import team.inreok.getiserver.domain.collector.provider.NormalizedCollectedJob

/** GETI가 실제로 노출할 가치가 있다고 판정한 공고의 관련도 범주다. */
enum class JobRelevanceCategory {
    /** SW/AI/데이터/인프라/보안/전산/금융 IT 등 학교 전공과 직접 관련된 직무. */
    DIRECT_IT,

    /** IoT/임베디드/펌웨어/전자/반도체/통신/제어/자동화/하드웨어 등 전공 연관 기술직. */
    RELATED_TECH,

    /** 고졸 공공기관 사무·행정·일반직, 은행 고졸 일반직, 청년인턴 등. */
    PUBLIC_FINANCE_GENERAL,
}

enum class JobEligibilityStatus {
    /** 저장·알림 대상. */
    ELIGIBLE,

    /** 저장은 하되, 자격 조건이 불명확해 별도 확인이 필요함을 표시한다(예: 정보처리 산업기능요원 편입 자격). */
    REVIEW_REQUIRED,

    /** 저장 대상에서 제외한다. */
    EXCLUDED,
}

data class JobEligibilityInput(
    val sourceCode: JobSourceCode,
    val title: String,
    val content: String?,
    val educationCondition: String?,
    val careerCondition: String?,
    val employmentType: String?,
    val jobFieldHint: String?,
    val qualificationDetail: String?,
    val militaryServiceType: String?,
)

data class JobEligibilityDecision(
    val status: JobEligibilityStatus,
    val category: JobRelevanceCategory?,
    val reason: String,
)

/**
 * 광주소프트웨어마이스터고 학생이 실제로 지원하거나 진로 탐색에 활용할 수 있는 공고인지
 * 판정하는 재현 가능한 규칙 기반 정책이다(Issue #62 확장 범위, "GETI 공고 수집 범위" 참고).
 * AI 모델을 호출하지 않는다. 판정 우선순위: 명시적 지원 불가 -> 학위·전문자격 필수 ->
 * 경력 필수·상위 직급 -> 업계별 제외(전문연구요원/금융 위탁영업/일반 생산직) -> 직무 관련성 ->
 * 고용형태 -> (판정 근거 기록). 마감·삭제·비공개 여부는 이 정책이 다루지 않고 Collector
 * 유효성 검증 단계(CollectorExecutionServiceImpl)에서 별도로 처리한다.
 */
object JobEligibilityPolicy {
    fun evaluate(job: NormalizedCollectedJob): JobEligibilityDecision =
        evaluate(
            JobEligibilityInput(
                sourceCode = job.sourceCode,
                title = job.title,
                content = job.content,
                educationCondition = job.educationCondition,
                careerCondition = job.careerCondition,
                employmentType = job.employmentType,
                jobFieldHint = job.jobFieldHint,
                qualificationDetail = job.qualificationDetail,
                militaryServiceType = job.militaryServiceType,
            ),
        )

    // 우선순위 단계마다 서로 다른 사유로 조기 반환해야 하므로 분기와 반환 지점이 많다. 각 단계가
    // 독립적인 판정 근거를 가지므로 하나의 when-식으로 합치면 오히려 추적하기 어렵다고 판단해
    // ReturnCount/CyclomaticComplexMethod를 그대로 둔다.
    @Suppress("ReturnCount", "CyclomaticComplexMethod")
    fun evaluate(input: JobEligibilityInput): JobEligibilityDecision {
        val text = buildSearchableText(input)

        if (containsAny(text, EXPLICIT_DISQUALIFY)) {
            return excluded("명시적으로 지원 불가 조건이 표시된 공고입니다.")
        }
        if (containsAny(text, EDUCATION_DEGREE_REQUIRED)) {
            return excluded("전문학사 이상 학위를 필수로 요구합니다.")
        }
        if (containsAny(text, PROFESSIONAL_LICENSE_EXCLUDE)) {
            return excluded("고졸 학생이 충족할 수 없는 전문 자격(면허 등)을 필수로 요구합니다.")
        }
        if (containsAny(text, CAREER_SENIOR_LEVEL)) {
            return excluded("팀장·시니어 등 상위 직급을 요구합니다.")
        }
        if (containsAny(text, CAREER_REQUIRED)) {
            return excluded("경력자만 지원 가능한 공고입니다.")
        }
        if (isMmaExcludedServiceType(input)) {
            return excluded("전문연구요원은 기본 수집 대상에서 제외합니다.")
        }
        if (containsAny(text, FINANCE_EXCLUDE)) {
            return excluded("보험설계·위탁영업 등 수수료 기반 금융 영업직입니다.")
        }
        if (isUnrelatedProductionJob(text)) {
            return excluded("전공과 무관한 단순 조립·포장 등 일반 생산직입니다.")
        }

        val classification =
            classifyCategory(text, input)
                ?: return excluded("직무 관련성을 확인할 수 있는 키워드를 찾지 못했습니다.")

        if (containsAny(text, EMPLOYMENT_TYPE_EXCLUDE)) {
            return excluded("일용직·단순 아르바이트 등 제외 대상 고용형태입니다.")
        }

        return if (classification.reviewRequired) {
            JobEligibilityDecision(
                JobEligibilityStatus.REVIEW_REQUIRED,
                classification.category,
                "정보처리 분야 산업기능요원은 편입 자격(전공·자격증·교육과정 등)을 원문에서 별도로 확인해야 합니다.",
            )
        } else {
            JobEligibilityDecision(JobEligibilityStatus.ELIGIBLE, classification.category, "수집 기준을 충족했습니다.")
        }
    }

    private fun excluded(reason: String) = JobEligibilityDecision(JobEligibilityStatus.EXCLUDED, null, reason)

    private fun isMmaExcludedServiceType(input: JobEligibilityInput): Boolean =
        input.sourceCode == JobSourceCode.MMA &&
            containsAny(normalizeForMatching(input.militaryServiceType.orEmpty()), MMA_EXCLUDE_SERVICE_TYPE)

    private fun isUnrelatedProductionJob(text: String): Boolean =
        containsAny(text, PRODUCTION_GENERAL_EXCLUDE) && !containsAny(text, PRODUCTION_TECH_CONTEXT)

    private data class CategoryClassification(
        val category: JobRelevanceCategory,
        val reviewRequired: Boolean,
    )

    // 직무 관련성 판정도 우선순위(병역일터 특수 규칙 -> 금융 IT -> 직접 IT -> 공공 IT -> 연관
    // 기술직 -> 생산기술 -> 공공 일반직)를 순서대로 확인해야 하므로 분기가 많다. 병역일터
    // 산업기능요원의 "정보처리" 편입 자격 확인 규칙(mmaClassification)은 다른 직무 관련성
    // Keyword보다 먼저 확인한다 — 정보처리 공고 원문이 "전산"/"개발자" 등 JOB_FIELD_DIRECT_IT
    // Keyword를 함께 포함하는 경우가 실제로 흔해서, 순서가 뒤였다면 REVIEW_REQUIRED 표시가
    // 조용히 건너뛰어졌다(자격 확인이 필요한 병역 특례 공고를 일반 DIRECT_IT처럼 취급하는 결함).
    @Suppress("ReturnCount")
    private fun classifyCategory(
        text: String,
        input: JobEligibilityInput,
    ): CategoryClassification? {
        mmaClassification(text, input)?.let { return it }

        if (containsAny(text, FINANCE_DIRECT_IT)) return CategoryClassification(JobRelevanceCategory.DIRECT_IT, false)
        if (containsAny(text, JOB_FIELD_DIRECT_IT)) return CategoryClassification(JobRelevanceCategory.DIRECT_IT, false)
        if (containsAny(
                text,
                PUBLIC_INSTITUTION_IT,
            )
        ) {
            return CategoryClassification(JobRelevanceCategory.DIRECT_IT, false)
        }
        if (containsAny(
                text,
                JOB_FIELD_RELATED_TECH,
            )
        ) {
            return CategoryClassification(JobRelevanceCategory.RELATED_TECH, false)
        }
        if (containsAny(text, PRODUCTION_GENERAL_EXCLUDE) && containsAny(text, PRODUCTION_TECH_CONTEXT)) {
            return CategoryClassification(JobRelevanceCategory.RELATED_TECH, false)
        }

        if (containsAny(text, PUBLIC_FINANCE_GENERAL_KEYWORDS)) {
            return CategoryClassification(JobRelevanceCategory.PUBLIC_FINANCE_GENERAL, false)
        }
        return null
    }

    private fun mmaClassification(
        text: String,
        input: JobEligibilityInput,
    ): CategoryClassification? {
        val isIndustrialTrainee =
            input.sourceCode == JobSourceCode.MMA &&
                containsAny(normalizeForMatching(input.militaryServiceType.orEmpty()), listOf("산업기능요원"))
        if (!isIndustrialTrainee) return null

        return when {
            containsAny(text, listOf("정보처리")) -> CategoryClassification(JobRelevanceCategory.DIRECT_IT, true)
            containsAny(text, MMA_PRIORITY_FIELD) -> CategoryClassification(JobRelevanceCategory.RELATED_TECH, false)
            else -> null
        }
    }

    private fun buildSearchableText(input: JobEligibilityInput): String =
        normalizeForMatching(
            listOfNotNull(
                input.title,
                input.content,
                input.educationCondition,
                input.careerCondition,
                input.employmentType,
                input.jobFieldHint,
                input.qualificationDetail,
            ).joinToString(separator = " "),
        )

    private fun containsAny(
        normalizedText: String,
        keywords: List<String>,
    ): Boolean = keywords.any { normalizedText.contains(normalizeForMatching(it)) }
}

/** 대소문자·공백·특수문자 차이로 인한 오탐/누락을 줄이기 위한 최소 정규화다. */
internal fun normalizeForMatching(raw: String): String =
    raw
        .lowercase()
        .replace(Regex("[\\s·・/\\\\,()\\[\\]{}\"'`~!?.\\-_]"), "")
