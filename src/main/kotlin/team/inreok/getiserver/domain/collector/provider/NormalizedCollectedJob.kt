package team.inreok.getiserver.domain.collector.provider

import team.inreok.getiserver.domain.collector.entity.type.JobDataQualityStatus
import team.inreok.getiserver.domain.collector.entity.type.JobSourceCode
import java.time.LocalDateTime

/**
 * Provider가 실제 외부 응답(XML/JSON 등)을 파싱한 뒤 돌려주는, Provider 독립적인 최소 정규화
 * 모델이다. 필수 정보가 일부 없어도 저장 대상에서 제외하지 않고 [dataQualityStatus]/[missingFields]로
 * 표현한다(누락 값을 빈 문자열·0·현재 날짜로 채우지 않는다). Provider 전용 필드(기관 코드, 페이지
 * 번호 등)는 여기 담지 않는다.
 */
data class NormalizedCollectedJob(
    val sourceCode: JobSourceCode,
    val externalJobId: String,
    val title: String,
    val companyName: String,
    val sourceUrl: String? = null,
    val content: String? = null,
    val externalUrl: String? = null,
    val startDate: LocalDateTime? = null,
    val endDate: LocalDateTime? = null,
    val rawUpdatedAt: LocalDateTime? = null,
    val collectedAt: LocalDateTime,
    val dataQualityStatus: JobDataQualityStatus,
    val missingFields: List<String> = emptyList(),
    // 아래는 마이스터고 학생 적합성 판정(JobEligibilityPolicy)에 필요한 Provider 원본 정보다.
    // 대부분은 Collector 내부 판정과 Discord 알림 표시에만 사용하고 Job Entity에는 반영하지
    // 않는다(Issue #62 확장 범위, "적합성 판정" 참고). 다만 [workRegion]과 [employmentType]은
    // 공고 카드가 표시해야 하는 값이라 Job Entity의 location/employmentType에도 반영한다
    // (Issue #169, CollectorExecutionServiceImpl 참고). Provider가 값을 주지 않으면 null이다.
    val educationCondition: String? = null,
    val careerCondition: String? = null,
    val employmentType: String? = null,
    val jobFieldHint: String? = null,
    val workRegion: String? = null,
    val qualificationDetail: String? = null,
    val recruitCount: String? = null,
    val militaryServiceType: String? = null,
)
