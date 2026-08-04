package team.inreok.getiserver.domain.collector.provider.cleaneye

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * `COLLECTOR_CLEAN_EYE_ENABLED`/`COLLECTOR_CLEAN_EYE_SERVICE_KEY` 환경변수가 바인딩되는
 * 대상이다(application.yaml의 `app.collector.provider.clean-eye.*`). Base URL·Endpoint는
 * 행정안전부 한국지역정보개발원_채용정보 조회 서비스(공공데이터포털 15159757)를 실제
 * 인증키로 호출해 확인했다(Issue #62 확장 범위, 최종 보고 참고).
 */
@ConfigurationProperties(prefix = "app.collector.provider.clean-eye")
data class CleanEyeProviderProperties(
    val enabled: Boolean = false,
    val serviceKey: String = "",
    val baseUrl: String = "https://apis.data.go.kr/B551982/openApiEmployInfo/openXmlEmployInfo",
    val connectTimeoutMs: Long = 5_000,
    val readTimeoutMs: Long = 10_000,
) {
    fun isConfigured(): Boolean = enabled && serviceKey.isNotBlank()
}
