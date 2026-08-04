package team.inreok.getiserver.domain.collector.provider.naraeilteo

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * `COLLECTOR_NARA_ILTEO_ENABLED`/`COLLECTOR_NARA_ILTEO_SERVICE_KEY` 환경변수가 바인딩되는
 * 대상이다(application.yaml의 `app.collector.provider.nara-ilteo.*`). Base URL(`/1760000/
 * PblJobService`)·목록(`getList`)/상세(`getItem`) Endpoint·Query Parameter·응답 필드를
 * 인사혁신처_공공취업정보 조회 서비스(공공데이터포털 15156780)의 실제 인증키 호출로 확인했다
 * (Issue #62 확장 범위, 최종 보고 참고).
 *
 * `getList`는 공고유형(idx)만 주고 본문(content)·원문 URL은 주지 않아, 목록에 있는 공고마다
 * `getItem`을 한 번 더 호출해야 한다(2단계 호출). 전체 데이터가 28만 건이 넘어 무제한으로
 * 순회하면 안 되므로, [initialLookbackDays](최초 수집 시 조회 시작일 계산에 사용)와
 * [maxDetailFetchesPerRun](실행당 상세 조회 최대 건수)으로 범위를 제한한다.
 */
@ConfigurationProperties(prefix = "app.collector.provider.nara-ilteo")
data class NaraIlteoProviderProperties(
    val enabled: Boolean = false,
    val serviceKey: String = "",
    val baseUrl: String = "https://apis.data.go.kr/1760000/PblJobService",
    val pageSize: Int = 50,
    val maxPages: Int = 50,
    val initialLookbackDays: Long = 90,
    val maxDetailFetchesPerRun: Int = 300,
    val connectTimeoutMs: Long = 5_000,
    val readTimeoutMs: Long = 10_000,
) {
    fun isConfigured(): Boolean = enabled && serviceKey.isNotBlank()
}
