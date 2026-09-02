package team.inreok.getiserver.domain.file.storage

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * `S3Presigner`가 실제로 쓰는 `resolvedPublicEndpoint()`의 대체(Fallback) 규칙을 검증한다.
 *
 * `docker compose --profile app`처럼 앱이 Container 안에서 돌면 `endpoint`가 Compose 내부 DNS
 * 이름(`http://minio:9000`)이라 외부 Client(Browser 등)가 Presigned URL을 열 수 없다 -- Presign
 * 전용 Endpoint(`publicEndpoint`)를 분리해 이 문제를 해결한다.
 */
class FileStoragePropertiesTest {
    @Test
    fun `publicEndpoint가 없으면 endpoint를 그대로 쓴다`() {
        val properties =
            baseProperties(
                endpoint = "http://minio:9000",
                publicEndpoint = null,
            )

        assertThat(properties.resolvedPublicEndpoint()).isEqualTo("http://minio:9000")
    }

    @Test
    fun `publicEndpoint가 빈 문자열이면 endpoint를 그대로 쓴다`() {
        val properties =
            baseProperties(
                endpoint = "http://minio:9000",
                publicEndpoint = "",
            )

        assertThat(properties.resolvedPublicEndpoint()).isEqualTo("http://minio:9000")
    }

    @Test
    fun `publicEndpoint가 있으면 endpoint 대신 publicEndpoint를 쓴다`() {
        val properties =
            baseProperties(
                endpoint = "http://minio:9000",
                publicEndpoint = "http://localhost:9000",
            )

        assertThat(properties.resolvedPublicEndpoint()).isEqualTo("http://localhost:9000")
    }

    @Test
    fun `endpoint와 publicEndpoint가 모두 없으면 null이다(운영 AWS 기본 Endpoint)`() {
        val properties =
            baseProperties(
                endpoint = null,
                publicEndpoint = null,
            )

        assertThat(properties.resolvedPublicEndpoint()).isNull()
    }

    private fun baseProperties(
        endpoint: String?,
        publicEndpoint: String?,
    ) = FileStorageProperties(
        bucket = "geti-test",
        region = "us-east-1",
        endpoint = endpoint,
        publicEndpoint = publicEndpoint,
    )
}
