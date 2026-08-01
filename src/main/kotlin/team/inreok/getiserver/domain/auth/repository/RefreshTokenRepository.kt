package team.inreok.getiserver.domain.auth.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import team.inreok.getiserver.domain.auth.entity.RefreshToken
import java.time.LocalDateTime

interface RefreshTokenRepository : JpaRepository<RefreshToken, Long> {
    fun findByTokenHash(tokenHash: String): RefreshToken?

    // 아직 폐기되지 않은 Token만 원자적으로 폐기하고 영향 행 수를 반환한다. 조회-검증-폐기를 별도
    // 문장으로 나누면 READ COMMITTED에서 동일 Token 동시 재발급 시 둘 다 통과할 수 있어(코드 리뷰
    // P1 반영), `revoked_at IS NULL` 조건을 건 단일 UPDATE로 한 요청만 1행을 갱신하게 한다.
    @Modifying
    @Query(
        "UPDATE RefreshToken r SET r.revokedAt = :now WHERE r.tokenHash = :tokenHash AND r.revokedAt IS NULL",
    )
    fun revokeIfActive(
        @Param("tokenHash") tokenHash: String,
        @Param("now") now: LocalDateTime,
    ): Int
}
