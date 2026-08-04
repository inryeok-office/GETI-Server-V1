package team.inreok.getiserver.domain.collector.provider.jobalio

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * `COLLECTOR_JOB_ALIO_ENABLED`/`COLLECTOR_JOB_ALIO_SERVICE_KEY` 환경변수가 바인딩되는 대상이다
 * (application.yaml의 `app.collector.provider.job-alio.*`). Base URL·Endpoint(`/list`)는
 * 재정경제부_공공기관 채용정보 조회서비스(공공데이터포털 15125273)를 실제 인증키로 호출해
 * 확인했다(Issue #62 확장 범위, 최종 보고 참고).
 */
@ConfigurationProperties(prefix = "app.collector.provider.job-alio")
data class JobAlioProviderProperties(
    val enabled: Boolean = false,
    val serviceKey: String = "",
    val baseUrl: String = "https://apis.data.go.kr/1051000/recruitment/list",
    val pageSize: Int = 50,
    val maxPages: Int = 200,
    val connectTimeoutMs: Long = 5_000,
    val readTimeoutMs: Long = 10_000,
) {
    fun isConfigured(): Boolean = enabled && serviceKey.isNotBlank()
}
