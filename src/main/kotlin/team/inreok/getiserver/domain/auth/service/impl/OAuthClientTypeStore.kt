package team.inreok.getiserver.domain.auth.service.impl

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import team.inreok.getiserver.domain.auth.service.OAuthClientType
import java.time.Duration

/**
 * `authorize` 요청의 `state`에 [OAuthClientType]을 묶어 짧은 TTL로 Redis에 보관한다(OAuth Web
 * Callback 결함 수정, Issue #162). [OAuthStateStore](Provider별 code_verifier/CSRF Marker)와
 * 별도 Key Namespace를 쓴다 -- 이 값은 "Provider와 code/state를 어떻게 교환하는가"가 아니라
 * "응답을 누구에게 어떤 모양으로 돌려줄까"라는 Controller 계층의 관심사라 Provider Client
 * 내부(`OAuthProviderClient.exchangeCode`)와 얽히지 않게 분리했다.
 *
 * `state`는 OAuth 요청 하나에서 항상 1회만 소비되므로(재사용 금지), 클라이언트 종류를 저장하지
 * 않은 요청(값이 없으면 `null`)은 기존 JSON 응답 동작으로 그대로 Fallback한다.
 */
@Component
class OAuthClientTypeStore(
    private val stringRedisTemplate: StringRedisTemplate,
) {
    fun save(
        state: String,
        clientType: OAuthClientType,
    ) {
        stringRedisTemplate.opsForValue().set(key(state), clientType.name, TTL)
    }

    // 저장된 값을 반환하고 즉시 삭제한다(OAuthStateStore.consume과 동일한 1회용 GETDEL 방식).
    // 값이 없거나(미지정 요청) 알 수 없는 문자열이면 null -- 호출부가 기존 JSON 응답으로
    // Fallback하는 안전한 기본 경로다.
    fun consume(state: String): OAuthClientType? =
        stringRedisTemplate
            .opsForValue()
            .getAndDelete(key(state))
            ?.let { value -> runCatching { OAuthClientType.valueOf(value) }.getOrNull() }

    private fun key(state: String): String = "$STATE_KEY_PREFIX$state"

    companion object {
        private const val STATE_KEY_PREFIX = "auth:oauth:client-type:"
        private val TTL: Duration = Duration.ofMinutes(10)
    }
}
