package team.inreok.getiserver.domain.collector.eligibility

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import team.inreok.getiserver.domain.collector.entity.type.JobSourceCode

/**
 * 광주소프트웨어마이스터고 학생 기준 공고 수집 범위 판정을 검증한다. AI 호출 없이 재현 가능한
 * 규칙 기반 판정이므로 각 우선순위 규칙(학력·경력·직무·고용형태)과 예시 시나리오를 그대로 옮겨
 * 테스트한다(Issue #62 확장 범위 "판정 우선순위" 참고).
 */
class JobEligibilityPolicyTest {
    private fun inputOf(
        sourceCode: JobSourceCode = JobSourceCode.JOB_ALIO,
        title: String = "",
        content: String? = null,
        educationCondition: String? = null,
        careerCondition: String? = null,
        employmentType: String? = null,
        jobFieldHint: String? = null,
        qualificationDetail: String? = null,
        militaryServiceType: String? = null,
    ) = JobEligibilityInput(
        sourceCode = sourceCode,
        title = title,
        content = content,
        educationCondition = educationCondition,
        careerCondition = careerCondition,
        employmentType = employmentType,
        jobFieldHint = jobFieldHint,
        qualificationDetail = qualificationDetail,
        militaryServiceType = militaryServiceType,
    )

    @Test
    fun `학력무관 전산직 신입이면 DIRECT_IT로 판정한다`() {
        val decision =
            JobEligibilityPolicy.evaluate(
                inputOf(title = "전산직 신입 채용", educationCondition = "학력무관", careerCondition = "신입"),
            )

        assertThat(decision.status).isEqualTo(JobEligibilityStatus.ELIGIBLE)
        assertThat(decision.category).isEqualTo(JobRelevanceCategory.DIRECT_IT)
    }

    @Test
    fun `학력무관이어도 상세 자격에 학사 필수가 있으면 제외한다`() {
        val decision =
            JobEligibilityPolicy.evaluate(
                inputOf(
                    title = "백엔드 개발자",
                    educationCondition = "학력무관",
                    qualificationDetail = "4년제 대학교 졸업 필수",
                ),
            )

        assertThat(decision.status).isEqualTo(JobEligibilityStatus.EXCLUDED)
    }

    @Test
    fun `개발자 공고라도 대졸 필수이면 제외한다`() {
        val decision =
            JobEligibilityPolicy.evaluate(
                inputOf(title = "개발자 채용", qualificationDetail = "대학교 졸업자만 지원 가능"),
            )

        assertThat(decision.status).isEqualTo(JobEligibilityStatus.EXCLUDED)
    }

    @Test
    fun `신입 경력 공고는 통과한다`() {
        val decision =
            JobEligibilityPolicy.evaluate(
                inputOf(title = "웹 개발 신입·경력 채용", careerCondition = "신입·경력"),
            )

        assertThat(decision.status).isEqualTo(JobEligibilityStatus.ELIGIBLE)
    }

    @Test
    fun `경력 우대이지만 신입도 지원 가능하면 통과한다`() {
        val decision =
            JobEligibilityPolicy.evaluate(
                inputOf(title = "프론트엔드 개발자", careerCondition = "경력 우대, 신입 지원 가능"),
            )

        assertThat(decision.status).isEqualTo(JobEligibilityStatus.ELIGIBLE)
    }

    @Test
    fun `경력자만 지원 가능하면 제외한다`() {
        val decision =
            JobEligibilityPolicy.evaluate(
                inputOf(title = "백엔드 개발자", careerCondition = "경력자만 지원 가능"),
            )

        assertThat(decision.status).isEqualTo(JobEligibilityStatus.EXCLUDED)
    }

    @Test
    fun `팀장 등 상위 직급 요구는 제외한다`() {
        val decision = JobEligibilityPolicy.evaluate(inputOf(title = "개발팀 팀장 채용"))

        assertThat(decision.status).isEqualTo(JobEligibilityStatus.EXCLUDED)
    }

    @Test
    fun `일반 생산직은 제외한다`() {
        val decision = JobEligibilityPolicy.evaluate(inputOf(title = "생산직 조립 사원 모집"))

        assertThat(decision.status).isEqualTo(JobEligibilityStatus.EXCLUDED)
    }

    @Test
    fun `반도체 생산기술은 RELATED_TECH로 포함한다`() {
        val decision =
            JobEligibilityPolicy.evaluate(
                inputOf(title = "반도체 생산 장비 유지보수", jobFieldHint = "반도체 생산기술"),
            )

        assertThat(decision.status).isEqualTo(JobEligibilityStatus.ELIGIBLE)
        assertThat(decision.category).isEqualTo(JobRelevanceCategory.RELATED_TECH)
    }

    @Test
    fun `졸업예정자 지원 가능한 임베디드 신입은 RELATED_TECH이다`() {
        val decision =
            JobEligibilityPolicy.evaluate(
                inputOf(
                    title = "임베디드 소프트웨어 신입",
                    educationCondition = "고교 졸업 예정자 지원 가능",
                    careerCondition = "신입",
                ),
            )

        assertThat(decision.status).isEqualTo(JobEligibilityStatus.ELIGIBLE)
        assertThat(decision.category).isEqualTo(JobRelevanceCategory.RELATED_TECH)
    }

    @Test
    fun `고졸 사무직 공기업 채용은 PUBLIC_FINANCE_GENERAL이다`() {
        val decision = JobEligibilityPolicy.evaluate(inputOf(title = "고졸 사무직 공기업 채용"))

        assertThat(decision.status).isEqualTo(JobEligibilityStatus.ELIGIBLE)
        assertThat(decision.category).isEqualTo(JobRelevanceCategory.PUBLIC_FINANCE_GENERAL)
    }

    @Test
    fun `공공기관 공고라도 간호사 면허 필수이면 제외한다`() {
        val decision =
            JobEligibilityPolicy.evaluate(
                inputOf(title = "공공기관 의료직 채용", qualificationDetail = "간호사 면허 필수"),
            )

        assertThat(decision.status).isEqualTo(JobEligibilityStatus.EXCLUDED)
    }

    @Test
    fun `은행 공고라도 보험설계 위탁영업이면 제외한다`() {
        val decision = JobEligibilityPolicy.evaluate(inputOf(title = "은행 보험설계 위탁영업"))

        assertThat(decision.status).isEqualTo(JobEligibilityStatus.EXCLUDED)
    }

    @Test
    fun `금융 IT는 DIRECT_IT이다`() {
        val decision = JobEligibilityPolicy.evaluate(inputOf(title = "은행 금융 IT 시스템 개발"))

        assertThat(decision.status).isEqualTo(JobEligibilityStatus.ELIGIBLE)
        assertThat(decision.category).isEqualTo(JobRelevanceCategory.DIRECT_IT)
    }

    @Test
    fun `산업기능요원 정보처리는 DIRECT_IT이지만 편입 자격 확인이 필요하다`() {
        val decision =
            JobEligibilityPolicy.evaluate(
                inputOf(
                    sourceCode = JobSourceCode.MMA,
                    title = "정보처리 산업기능요원 모집",
                    militaryServiceType = "산업기능요원",
                ),
            )

        assertThat(decision.status).isEqualTo(JobEligibilityStatus.REVIEW_REQUIRED)
        assertThat(decision.category).isEqualTo(JobRelevanceCategory.DIRECT_IT)
    }

    @Test
    fun `산업기능요원 정보처리 공고 원문에 전산 직무 Keyword가 함께 있어도 편입 자격 확인이 필요하다`() {
        // classifyCategory가 일반 JOB_FIELD_DIRECT_IT Keyword("전산")를 mmaClassification보다
        // 먼저 확인하면 REVIEW_REQUIRED가 조용히 ELIGIBLE로 바뀌던 회귀를 방지한다.
        val decision =
            JobEligibilityPolicy.evaluate(
                inputOf(
                    sourceCode = JobSourceCode.MMA,
                    title = "정보처리 산업기능요원 모집",
                    content = "전산실에서 개발자 업무를 보조합니다.",
                    militaryServiceType = "산업기능요원",
                ),
            )

        assertThat(decision.status).isEqualTo(JobEligibilityStatus.REVIEW_REQUIRED)
        assertThat(decision.category).isEqualTo(JobRelevanceCategory.DIRECT_IT)
    }

    @Test
    fun `전문연구요원은 기본 제외 대상이다`() {
        val decision =
            JobEligibilityPolicy.evaluate(
                inputOf(sourceCode = JobSourceCode.MMA, title = "전문연구요원 모집", militaryServiceType = "전문연구요원"),
            )

        assertThat(decision.status).isEqualTo(JobEligibilityStatus.EXCLUDED)
    }

    @Test
    fun `산업기능요원 전기 분야는 RELATED_TECH이다`() {
        val decision =
            JobEligibilityPolicy.evaluate(
                inputOf(
                    sourceCode = JobSourceCode.MMA,
                    title = "전기 설비 산업기능요원 모집",
                    militaryServiceType = "산업기능요원",
                ),
            )

        assertThat(decision.status).isEqualTo(JobEligibilityStatus.ELIGIBLE)
        assertThat(decision.category).isEqualTo(JobRelevanceCategory.RELATED_TECH)
    }

    @Test
    fun `일용직 등 제외 대상 고용형태는 제외한다`() {
        val decision =
            JobEligibilityPolicy.evaluate(
                inputOf(title = "전산직 지원", employmentType = "일용직"),
            )

        assertThat(decision.status).isEqualTo(JobEligibilityStatus.EXCLUDED)
    }

    @Test
    fun `직무 관련성을 확인할 수 없으면 제외한다`() {
        val decision = JobEligibilityPolicy.evaluate(inputOf(title = "카페 매장 관리"))

        assertThat(decision.status).isEqualTo(JobEligibilityStatus.EXCLUDED)
    }

    @Test
    fun `대소문자 공백 특수문자가 달라도 동일하게 판정한다`() {
        val decision1 = JobEligibilityPolicy.evaluate(inputOf(title = "DevOps 엔지니어 채용"))
        val decision2 = JobEligibilityPolicy.evaluate(inputOf(title = "  D e v O p s  엔지니어  채용  "))

        assertThat(decision1.status).isEqualTo(JobEligibilityStatus.ELIGIBLE)
        assertThat(decision2.status).isEqualTo(decision1.status)
        assertThat(decision2.category).isEqualTo(decision1.category)
    }

    @Test
    fun `긍정 키워드와 제외 조건이 함께 있으면 제외가 우선한다`() {
        val decision =
            JobEligibilityPolicy.evaluate(
                inputOf(title = "백엔드 개발자 모집", qualificationDetail = "석사 이상 필수"),
            )

        assertThat(decision.status).isEqualTo(JobEligibilityStatus.EXCLUDED)
    }
}
