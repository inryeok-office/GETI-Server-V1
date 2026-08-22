package team.inreok.getiserver.domain.auth.service.impl

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import team.inreok.getiserver.domain.auth.service.OAuthLoginIntent
import java.time.Duration

/**
 * `authorize` 요청의 `state`에 [OAuthLoginIntent]를 묶어 짧은 TTL로 Redis에 보관한다(Issue #229).
 * [OAuthClientTypeStore]와 같은 구조·이유이며 Key Namespace만 다르다 -- "응답을 어떤 모양으로
 * 돌려줄까"(clientType)와 "이 로그인이 재신청인가"(intent)는 서로 다른 관심사라 분리한다.
 *
 * `state`는 OAuth 요청 하나에서 1회만 소비되므로(재사용 금지), 의도를 저장하지 않은 요청(값이 없으면
 * `null`)은 재신청이 아닌 일반 로그인으로 그대로 처리된다.
 */
@Component
class OAuthLoginIntentStore(
    private val stringRedisTemplate: StringRedisTemplate,
) {
    fun save(
        state: String,
        intent: OAuthLoginIntent,
    ) {
        stringRedisTemplate.opsForValue().set(key(state), intent.name, TTL)
    }

    // 저장된 값을 반환하고 즉시 삭제한다(1회용 GETDEL). 값이 없거나 알 수 없는 문자열이면 null --
    // 호출부가 일반 로그인으로 Fallback하는 안전한 기본 경로다.
    fun consume(state: String): OAuthLoginIntent? =
        stringRedisTemplate
            .opsForValue()
            .getAndDelete(key(state))
            ?.let { value -> runCatching { OAuthLoginIntent.valueOf(value) }.getOrNull() }

    private fun key(state: String): String = "$STATE_KEY_PREFIX$state"

    companion object {
        private const val STATE_KEY_PREFIX = "auth:oauth:login-intent:"
        private val TTL: Duration = Duration.ofMinutes(10)
    }
}
