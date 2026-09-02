package team.inreok.getiserver.domain.auth.service.impl

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.auth.entity.RefreshToken
import team.inreok.getiserver.domain.auth.exception.InvalidRefreshTokenException
import team.inreok.getiserver.domain.auth.repository.RefreshTokenRepository
import team.inreok.getiserver.domain.auth.service.IssuedTokens
import team.inreok.getiserver.domain.auth.service.TokenService
import team.inreok.getiserver.domain.member.query.OAuthMemberPort
import team.inreok.getiserver.global.security.JwtProperties
import team.inreok.getiserver.global.security.JwtTokenProvider
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.LocalDateTime
import java.util.Base64

@Service
class TokenServiceImpl(
    private val refreshTokenRepository: RefreshTokenRepository,
    private val jwtTokenProvider: JwtTokenProvider,
    private val jwtProperties: JwtProperties,
    private val oAuthMemberPort: OAuthMemberPort,
) : TokenService {
    @Transactional
    override fun issueFor(
        memberId: Long,
        roles: List<String>,
        deviceIdentifier: String?,
    ): IssuedTokens {
        val accessToken = jwtTokenProvider.createAccessToken(memberId, roles)
        val refreshToken = issueRefreshToken(memberId, deviceIdentifier)
        return IssuedTokens(
            accessToken = accessToken,
            refreshToken = refreshToken,
            accessTokenExpiresInSeconds = jwtProperties.accessTokenExpirationSeconds,
        )
    }

    @Transactional
    override fun refresh(
        refreshToken: String,
        deviceIdentifier: String?,
    ): IssuedTokens {
        val tokenHash = hash(refreshToken)
        val existing = loadNotExpired(tokenHash)
        // 원자적 폐기: 동시에 같은 Token으로 재발급하면 한 요청만 1행을 갱신하고 나머지는 0행이라
        // 거부된다(탈취된 Refresh Token 재사용 탐지, 코드 리뷰 P1 반영).
        if (refreshTokenRepository.revokeIfActive(tokenHash, LocalDateTime.now()) == 0) {
            throw InvalidRefreshTokenException()
        }

        val roles = oAuthMemberPort.getRoles(existing.memberId)
        val newAccessToken = jwtTokenProvider.createAccessToken(existing.memberId, roles)
        val newRefreshToken = issueRefreshToken(existing.memberId, deviceIdentifier ?: existing.deviceIdentifier)
        return IssuedTokens(
            accessToken = newAccessToken,
            refreshToken = newRefreshToken,
            accessTokenExpiresInSeconds = jwtProperties.accessTokenExpirationSeconds,
        )
    }

    @Transactional
    override fun logout(
        refreshToken: String,
        memberId: Long,
    ) {
        val tokenHash = hash(refreshToken)
        val existing = refreshTokenRepository.findByTokenHash(tokenHash) ?: throw InvalidRefreshTokenException()
        // 소유권 검증: 호출자가 이 Refresh Token의 소유자가 아니면 거부한다(코드 리뷰 Blocker 반영).
        if (existing.memberId != memberId) throw InvalidRefreshTokenException()
        // 이미 폐기됐어도(0행) 로그아웃은 멱등하게 성공으로 둔다. 폐기 자체는 원자적 UPDATE로 처리한다.
        refreshTokenRepository.revokeIfActive(tokenHash, LocalDateTime.now())
    }

    private fun loadNotExpired(tokenHash: String): RefreshToken {
        val found = refreshTokenRepository.findByTokenHash(tokenHash) ?: throw InvalidRefreshTokenException()
        if (found.expiresAt.isBefore(LocalDateTime.now())) throw InvalidRefreshTokenException()
        return found
    }

    private fun issueRefreshToken(
        memberId: Long,
        deviceIdentifier: String?,
    ): String {
        val raw = randomToken()
        val entity =
            RefreshToken(
                memberId = memberId,
                tokenHash = hash(raw),
                expiresAt = LocalDateTime.now().plusSeconds(jwtProperties.refreshTokenExpirationSeconds),
            )
        entity.deviceIdentifier = deviceIdentifier
        refreshTokenRepository.save(entity)
        return raw
    }

    private fun randomToken(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    // Refresh Token 원문은 저장하지 않고 해시만 저장/조회한다(docs/architecture/erd.md
    // "refresh_tokens.token_hash는 GETI가 자체 발급한 Refresh Token의 해시만 담는다" 참고).
    private fun hash(raw: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(raw.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
