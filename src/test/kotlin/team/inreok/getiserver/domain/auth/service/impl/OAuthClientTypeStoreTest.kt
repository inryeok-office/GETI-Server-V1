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
import team.inreok.getiserver.domain.auth.service.OAuthClientType
import java.time.Duration

@ExtendWith(MockitoExtension::class)
class OAuthClientTypeStoreTest {
    @Mock
    private lateinit var stringRedisTemplate: StringRedisTemplate

    @Mock
    private lateinit var valueOperations: ValueOperations<String, String>

    private val store by lazy { OAuthClientTypeStore(stringRedisTemplate) }

    @Test
    fun `save는 state에 clientType 이름을 짧은 TTL로 저장한다`() {
        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations)

        store.save("state-1", OAuthClientType.WEB)

        verify(valueOperations).set("auth:oauth:client-type:state-1", "WEB", Duration.ofMinutes(10))
    }

    @Test
    fun `consume은 저장된 값을 clientType으로 변환해 반환한다`() {
        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations)
        given(valueOperations.getAndDelete("auth:oauth:client-type:state-1")).willReturn("WEB")

        assertThat(store.consume("state-1")).isEqualTo(OAuthClientType.WEB)
    }

    @Test
    fun `consume은 저장된 값이 없으면 null을 반환한다`() {
        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations)
        given(valueOperations.getAndDelete("auth:oauth:client-type:state-1")).willReturn(null)

        assertThat(store.consume("state-1")).isNull()
    }

    @Test
    fun `consume은 알 수 없는 값이 저장돼 있으면 null을 반환한다`() {
        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations)
        given(valueOperations.getAndDelete("auth:oauth:client-type:state-1")).willReturn("GARBAGE")

        assertThat(store.consume("state-1")).isNull()
    }
}
