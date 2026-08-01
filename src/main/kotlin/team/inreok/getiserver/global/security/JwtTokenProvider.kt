package team.inreok.getiserver.global.security

import io.jsonwebtoken.Claims
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.modulith.NamedInterface
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.Date
import javax.crypto.SecretKey

/**
 * Access Token(JWT)의 발급과 검증만 담당한다. Refresh Token은 JWT가 아니라 무작위 문자열을
 * 해시로 저장/조회하므로([RefreshToken][team.inreok.getiserver.domain.auth.entity.RefreshToken])
 * 이 Class가 다루지 않는다.
 *
 * global은 domain을 참조할 수 없으므로(PackageArchitectureTest), 이 Class는 memberId/roles를
 * 원시 타입(Long/String)으로만 다루고 Member의 RoleType 같은 Domain Enum에 의존하지 않는다.
 * domain.auth(TokenServiceImpl, JwtAuthenticationFilter)가 Token 발급/검증에 직접 사용하므로
 * Named Interface로 공개한다(docs/architecture/modularity.md "Domain 간 허용 의존" 참고).
 */
@NamedInterface
@Component
@EnableConfigurationProperties(JwtProperties::class)
class JwtTokenProvider(
    private val jwtProperties: JwtProperties,
) {
    private val key: SecretKey = Keys.hmacShaKeyFor(jwtProperties.secret.toByteArray())

    fun createAccessToken(
        memberId: Long,
        roles: List<String>,
    ): String {
        val now = Instant.now()
        return Jwts
            .builder()
            .subject(memberId.toString())
            .claim(ROLES_CLAIM, roles)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(jwtProperties.accessTokenExpirationSeconds)))
            .signWith(key)
            .compact()
    }

    // 서명·형식이 유효하지 않거나 만료된 Token은 null을 반환한다(예외를 Filter까지 전파하지 않고
    // "인증되지 않음"으로 처리하기 위함). 원본 예외는 의도적으로 무시한다.
    @Suppress("SwallowedException")
    fun parseOrNull(token: String): Claims? =
        try {
            Jwts
                .parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .payload
        } catch (ex: JwtException) {
            null
        } catch (ex: IllegalArgumentException) {
            null
        }

    companion object {
        const val ROLES_CLAIM = "roles"

        @Suppress("UNCHECKED_CAST")
        fun rolesOf(claims: Claims): List<String> = (claims[ROLES_CLAIM] as? List<String>).orEmpty()

        fun memberIdOf(claims: Claims): Long = claims.subject.toLong()
    }
}
