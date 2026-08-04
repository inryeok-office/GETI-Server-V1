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
)
