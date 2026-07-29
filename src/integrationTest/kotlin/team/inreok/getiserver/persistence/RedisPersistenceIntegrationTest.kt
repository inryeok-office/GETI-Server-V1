package team.inreok.getiserver.persistence

import com.redis.testcontainers.RedisContainer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.redis.test.autoconfigure.DataRedisTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.data.redis.core.StringRedisTemplate
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName

/**
 * Redis Testcontainers를 이용한 Persistence 통합 테스트다. 공식 `StringRedisTemplate`으로
 * 최소한의 PING/SET/GET/DELETE 동작만 검증한다. 실제 서비스 Key Namespace나 Cache 정책은
 * 이번 범위가 아니다.
 */
@Testcontainers
@DataRedisTest
class RedisPersistenceIntegrationTest
    @Autowired
    constructor(
        private val redisTemplate: StringRedisTemplate,
    ) {
        @Test
        fun `문자열 값을 저장하고 다시 조회한 뒤 삭제할 수 있다`() {
            val key = "integration:persistence:probe"

            redisTemplate.opsForValue().set(key, "redis-integration-test")

            assertThat(redisTemplate.opsForValue().get(key)).isEqualTo("redis-integration-test")

            redisTemplate.delete(key)

            assertThat(redisTemplate.opsForValue().get(key)).isNull()
        }

        companion object {
            @Container
            @ServiceConnection
            @JvmStatic
            val redis = RedisContainer(DockerImageName.parse("redis:8.8.1-alpine"))
        }
    }
