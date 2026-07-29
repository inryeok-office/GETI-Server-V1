package team.inreok.getiserver.global.web

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * `allowedOrigins`가 비어 있으면(기본값) CORS Mapping을 등록하지 않는다. 이는 "모든 Origin
 * 허용"이 아니라 "CORS 자체를 비활성화"를 의미하며, Browser의 기본 Same-Origin 정책만 적용된다.
 * 실제 Web Client Origin이 확정되면 환경 변수(`APP_WEB_CORS_ALLOWED_ORIGINS` 등)로 값을 채운다.
 */
@ConfigurationProperties(prefix = "app.web.cors")
data class CorsProperties(
    val allowedOrigins: List<String> = emptyList(),
    val allowedMethods: List<String> = listOf("GET", "POST", "PUT", "PATCH", "DELETE"),
    val allowedHeaders: List<String> = listOf("*"),
    val allowCredentials: Boolean = false,
) {
    init {
        check(!(allowCredentials && allowedOrigins.contains("*"))) {
            "allowCredentials=true 상태에서 allowedOrigins에 '*'를 포함할 수 없습니다."
        }
    }
}
