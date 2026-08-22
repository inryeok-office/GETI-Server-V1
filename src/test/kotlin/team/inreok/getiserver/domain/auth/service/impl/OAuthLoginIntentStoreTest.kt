package team.inreok.getiserver.domain.auth.service.impl

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import team.inreok.getiserver.domain.auth.service.OAuthLoginIntent
import java.time.Duration

@ExtendWith(MockitoExtension::class)
class OAuthLoginIntentStoreTest {
    @Mock
    private lateinit var stringRedisTemplate: StringRedisTemplate

    @Mock
    private lateinit var valueOperations: ValueOperations<String, String>

    private val store by lazy { OAuthLoginIntentStore(stringRedisTemplate) }

    @Test
    fun `save는 state에 intent 이름을 짧은 TTL로 저장한다`() {
        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations)

        store.save("state-1", OAuthLoginIntent.REAPPLY)

        verify(valueOperations).set("auth:oauth:login-intent:state-1", "REAPPLY", Duration.ofMinutes(10))
    }

    @Test
    fun `consume은 저장된 값을 intent로 변환해 반환한다`() {
        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations)
        given(valueOperations.getAndDelete("auth:oauth:login-intent:state-1")).willReturn("REAPPLY")

        assertThat(store.consume("state-1")).isEqualTo(OAuthLoginIntent.REAPPLY)
    }

    @Test
    fun `consume은 저장된 값이 없으면 null을 반환한다`() {
        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations)
        given(valueOperations.getAndDelete("auth:oauth:login-intent:state-1")).willReturn(null)

        assertThat(store.consume("state-1")).isNull()
    }

    @Test
    fun `consume은 알 수 없는 값이 저장돼 있으면 null을 반환한다`() {
        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations)
        given(valueOperations.getAndDelete("auth:oauth:login-intent:state-1")).willReturn("GARBAGE")

        assertThat(store.consume("state-1")).isNull()
    }
}
