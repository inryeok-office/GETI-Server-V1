package team.inreok.getiserver.domain.collector.provider.mma

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * `COLLECTOR_MMA_ENABLED`/`COLLECTOR_MMA_SERVICE_KEY` 환경변수가 바인딩되는 대상이다
 * (application.yaml의 `app.collector.provider.mma.*`). `serviceKey`는 공공데이터포털이
 * 배포하는 형태 그대로(URL Encoding 여부 무관) 받아 [team.inreok.getiserver.domain.collector.provider.ServiceKeyCodec]가
 * 호출 시점에 안전하게 처리한다 — 여기서 값을 미리 가공하지 않는다.
 */
@ConfigurationProperties(prefix = "app.collector.provider.mma")
data class MmaProviderProperties(
    val enabled: Boolean = false,
    val serviceKey: String = "",
    val baseUrl: String = "https://apis.data.go.kr/1300000/CyJeongBo/list",
    val pageSize: Int = 50,
    // 한 번의 수집 실행에서 조회할 수 있는 최대 페이지 수(안전장치). Provider가 pageNo를
    // 무시하거나 totalCount를 신뢰할 수 없을 때 무한 루프로 이어지지 않도록 상한을 둔다.
    val maxPages: Int = 200,
    val connectTimeoutMs: Long = 5_000,
    val readTimeoutMs: Long = 10_000,
) {
    fun isConfigured(): Boolean = enabled && serviceKey.isNotBlank()
}
