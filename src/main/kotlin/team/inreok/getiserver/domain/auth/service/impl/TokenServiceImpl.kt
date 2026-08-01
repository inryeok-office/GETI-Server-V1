package team.inreok.getiserver.domain.auth.service.impl

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.auth.entity.RefreshToken
import team.inreok.getiserver.domain.auth.exception.InvalidRefreshTokenException
import team.inreok.getiserver.domain.auth.repository.RefreshTokenRepository
import team.inreok.getiserver.domain.auth.service.IssuedTokens
import team.inreok.getiserver.domain.auth.service.TokenService
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
) : TokenService {
    @Transactional
    override fun refresh(
        refreshToken: String,
        deviceIdentifier: String?,
    ): IssuedTokens {
        val existing = findActive(refreshToken)
        existing.revokedAt = LocalDateTime.now()

        val newAccessToken = jwtTokenProvider.createAccessToken(existing.memberId, emptyList())
        val newRefreshToken = issueRefreshToken(existing.memberId, deviceIdentifier ?: existing.deviceIdentifier)
        return IssuedTokens(
            accessToken = newAccessToken,
            refreshToken = newRefreshToken,
            accessTokenExpiresInSeconds = jwtProperties.accessTokenExpirationSeconds,
        )
    }

    @Transactional
    override fun logout(refreshToken: String) {
        val existing = findActive(refreshToken)
        existing.revokedAt = LocalDateTime.now()
    }

    private fun findActive(rawToken: String): RefreshToken {
        val found = refreshTokenRepository.findByTokenHash(hash(rawToken))
        if (found == null || found.revokedAt != null || found.expiresAt.isBefore(LocalDateTime.now())) {
            throw InvalidRefreshTokenException()
        }
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
