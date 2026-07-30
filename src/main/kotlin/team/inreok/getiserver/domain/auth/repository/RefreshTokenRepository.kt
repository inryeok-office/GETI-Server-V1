package team.inreok.getiserver.domain.auth.repository

import org.springframework.data.jpa.repository.JpaRepository
import team.inreok.getiserver.domain.auth.entity.RefreshToken

interface RefreshTokenRepository : JpaRepository<RefreshToken, Long> {
    fun findByTokenHash(tokenHash: String): RefreshToken?
}
