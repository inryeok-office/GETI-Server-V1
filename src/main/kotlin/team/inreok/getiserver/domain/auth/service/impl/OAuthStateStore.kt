package team.inreok.getiserver.domain.auth.service.impl

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * OAuth 인가 요청의 `state`와 그에 묶인 값(Google은 PKCE `code_verifier`, DG는 CSRF 검증용 Marker)을
 * 짧은 TTL로 Redis에 보관한다. `state`/`code_verifier`는 PostgreSQL에 저장하지 않는다(1회용, 만료,
 * docs/architecture/erd.md "OAuth / 회원 정책 요약" 참고). Google/DG Provider Client가 공유한다.
 */
@Component
class OAuthStateStore(
    private val stringRedisTemplate: StringRedisTemplate,
) {
    fun save(
        state: String,
        value: String,
    ) {
        stringRedisTemplate.opsForValue().set(key(state), value, STATE_TTL)
    }

    // 저장된 값을 반환하고 즉시 삭제한다(1회용). 없으면(만료·위조) null. 조회와 삭제를 한 명령
    // (GETDEL, Redis 6.2+)으로 원자화해 동일 state의 동시 소비(이중 클릭·재시도)를 막는다(코드 리뷰 P2 반영).
    fun consume(state: String): String? = stringRedisTemplate.opsForValue().getAndDelete(key(state))

    private fun key(state: String): String = "$STATE_KEY_PREFIX$state"

    companion object {
        private const val STATE_KEY_PREFIX = "auth:oauth:state:"
        private val STATE_TTL: Duration = Duration.ofMinutes(10)
    }
}
