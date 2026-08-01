package team.inreok.getiserver.global.security

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.modulith.NamedInterface

// Secret은 Profile별 실제 값이 필요해 여기(공통 설정)에는 기본값을 두지 않고
// application-local.yaml/application-prod.yaml에서만 정의한다(datasource 설정과 동일한 방식).
// TokenServiceImpl(domain.auth)이 Access/Refresh Token 만료 시간을 읽기 위해 참조하므로
// Named Interface로 공개한다(docs/architecture/modularity.md "Domain 간 허용 의존" 참고).
@NamedInterface
@ConfigurationProperties(prefix = "app.jwt")
data class JwtProperties(
    val secret: String,
    val accessTokenExpirationSeconds: Long,
    val refreshTokenExpirationSeconds: Long,
)
