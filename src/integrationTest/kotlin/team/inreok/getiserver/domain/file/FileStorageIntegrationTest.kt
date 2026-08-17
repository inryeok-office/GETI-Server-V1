package team.inreok.getiserver.domain.file

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.testcontainers.containers.MinIOContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import software.amazon.awssdk.services.s3.S3Client
import team.inreok.getiserver.domain.file.exception.FileStorageException
import team.inreok.getiserver.domain.file.storage.FileStorageConfig
import team.inreok.getiserver.domain.file.storage.FileStorageProperties
import team.inreok.getiserver.domain.file.storage.S3FileStorageAdapter
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.UUID

/**
 * 실제 S3 Compatible Storage(MinIO)에 대해 [S3FileStorageAdapter]를 검증한다.
 *
 * Unit Test의 Fake로는 확인할 수 없는 것들이 대상이다.
 * - `endpointOverride` + `forcePathStyle` 설정이 실제로 통하는지
 * - Presigned URL이 유효한 서명을 만들고 그 URL로 정말 내려받을 수 있는지
 * - Presign에 함께 실은 `Content-Disposition`/`Content-Type`을 Storage가 응답에 적용하는지
 *
 * Spring Context를 띄우지 않고 Adapter만 직접 조립한다 -- 검증 대상이 SDK 설정과 Storage
 * 동작이지 Bean 배선이 아니기 때문이다.
 */
@Testcontainers
class FileStorageIntegrationTest {
    @Test
    fun `업로드한 Object를 조회하고 삭제할 수 있다`() {
        val key = "PROFILE_IMAGE/2026/08/${UUID.randomUUID()}"

        adapter.upload(key, "image/png", CONTENT.size.toLong(), ByteArrayInputStream(CONTENT))

        assertThat(adapter.exists(key)).isTrue()

        adapter.delete(key)
        assertThat(adapter.exists(key)).isFalse()
    }

    @Test
    fun `존재하지 않는 Object는 exists가 false다`() {
        assertThat(adapter.exists("PROFILE_IMAGE/2026/08/${UUID.randomUUID()}")).isFalse()
    }

    @Test
    fun `없는 Object를 삭제해도 오류가 아니다`() {
        // 업로드 실패 후 보상 삭제가 여러 번 호출될 수 있어 멱등해야 한다.
        adapter.delete("PROFILE_IMAGE/2026/08/${UUID.randomUUID()}")
    }

    @Test
    fun `Presigned URL로 실제 내용을 내려받을 수 있다`() {
        val key = "PROFILE_IMAGE/2026/08/${UUID.randomUUID()}"
        adapter.upload(key, "image/png", CONTENT.size.toLong(), ByteArrayInputStream(CONTENT))

        val url = adapter.presignedGetUrl(key, "attachment; filename=\"logo.png\"", "image/png", TTL)
        val connection = url.toURL().openConnection() as HttpURLConnection

        assertThat(connection.responseCode).isEqualTo(HttpURLConnection.HTTP_OK)
        assertThat(connection.inputStream.readBytes()).isEqualTo(CONTENT)
    }

    @Test
    fun `Presign에 실은 Content-Disposition과 Content-Type을 Storage가 응답에 적용한다`() {
        // 이것이 성립해야 Presigned URL 방식에서도 파일명 Encoding(§28)과 위험한 형식의
        // Content-Type 강등(§27)을 서버가 계속 통제할 수 있다.
        val key = "PROFILE_IMAGE/2026/08/${UUID.randomUUID()}"
        adapter.upload(key, "image/png", CONTENT.size.toLong(), ByteArrayInputStream(CONTENT))
        val disposition = "attachment; filename*=UTF-8''%EC%9D%B4%EB%A0%A5%EC%84%9C.png"

        val url = adapter.presignedGetUrl(key, disposition, "application/octet-stream", TTL)
        val connection = url.toURL().openConnection() as HttpURLConnection

        assertThat(connection.responseCode).isEqualTo(HttpURLConnection.HTTP_OK)
        assertThat(connection.getHeaderField("Content-Disposition")).isEqualTo(disposition)
        assertThat(connection.getHeaderField("Content-Type")).isEqualTo("application/octet-stream")
    }

    @Test
    fun `Presigned URL의 서명을 고치면 거부된다`() {
        // 클라이언트가 Query를 바꿔 다른 Content-Disposition을 요구할 수 없어야 한다.
        val key = "PROFILE_IMAGE/2026/08/${UUID.randomUUID()}"
        adapter.upload(key, "image/png", CONTENT.size.toLong(), ByteArrayInputStream(CONTENT))
        val url = adapter.presignedGetUrl(key, "attachment; filename=\"logo.png\"", "image/png", TTL)

        val tampered = URI.create(url.toString().replace("attachment", "inline"))
        val connection = tampered.toURL().openConnection() as HttpURLConnection

        assertThat(connection.responseCode).isEqualTo(HttpURLConnection.HTTP_FORBIDDEN)
    }

    @Test
    fun `업로드한 내용을 download로 그대로 읽을 수 있다`() {
        // FileArchivePort(Issue #84 Phase 6)가 여러 File을 ZIP으로 묶을 때 이 Method로 각 File의
        // 내용을 읽는다.
        val key = "PROFILE_IMAGE/2026/08/${UUID.randomUUID()}"
        adapter.upload(key, "image/png", CONTENT.size.toLong(), ByteArrayInputStream(CONTENT))

        adapter.download(key).use { assertThat(it.readBytes()).isEqualTo(CONTENT) }
    }

    @Test
    fun `존재하지 않는 Object를 download하면 FileStorageException이다`() {
        assertThatThrownBy {
            adapter.download("PROFILE_IMAGE/2026/08/${UUID.randomUUID()}")
        }.isInstanceOf(FileStorageException::class.java)
    }

    @Test
    fun `Bucket이 없으면 업로드가 FileStorageException으로 변환된다`() {
        // SDK 예외 Message에는 Bucket 이름과 Request ID가 들어 있어 그대로 노출하면 안 된다(§26/§42).
        val key = "PROFILE_IMAGE/2026/08/${UUID.randomUUID()}"

        assertThatThrownBy {
            missingBucketAdapter.upload(key, "image/png", CONTENT.size.toLong(), ByteArrayInputStream(CONTENT))
        }.isInstanceOf(FileStorageException::class.java)
            .hasMessage("파일 저장소 처리 중 오류가 발생했습니다.")
    }

    @Test
    fun `Object Key에는 원본 파일명이 들어가지 않는다`() {
        // Key 규칙 자체는 Unit Test가 검증하지만, 한글 파일명을 담은 Object가 실제 Storage에서
        // 정상 동작하는지는 여기서 확인한다.
        val key = "PROFILE_IMAGE/2026/08/${UUID.randomUUID()}"
        adapter.upload(key, "image/png", CONTENT.size.toLong(), ByteArrayInputStream(CONTENT))

        val disposition = "attachment; filename*=UTF-8''%EC%9D%B4%EB%A0%A5%EC%84%9C.png"
        val url = adapter.presignedGetUrl(key, disposition, "image/png", TTL)

        assertThat(url.toString()).doesNotContain("이력서")
        val decoded = URLDecoder.decode(url.toString(), StandardCharsets.UTF_8)
        assertThat(decoded).contains(key)
    }

    companion object {
        private val CONTENT = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        private val TTL: Duration = Duration.ofMinutes(5)
        private const val BUCKET = "geti-integration-test"

        @Container
        @JvmStatic
        val minio = MinIOContainer(DockerImageName.parse("minio/minio:RELEASE.2025-09-07T16-13-09Z"))

        private lateinit var adapter: S3FileStorageAdapter

        /** Bucket을 만들지 않아 Storage 오류 경로를 재현하는 Adapter. */
        private lateinit var missingBucketAdapter: S3FileStorageAdapter

        @BeforeAll
        @JvmStatic
        fun setUp() {
            adapter = adapterFor(BUCKET, autoCreateBucket = true)
            missingBucketAdapter = adapterFor("bucket-that-does-not-exist", autoCreateBucket = false)
        }

        private fun adapterFor(
            bucket: String,
            autoCreateBucket: Boolean,
        ): S3FileStorageAdapter {
            val properties =
                FileStorageProperties(
                    bucket = bucket,
                    region = "us-east-1",
                    endpoint = minio.s3URL,
                    pathStyleAccess = true,
                    accessKey = minio.userName,
                    secretKey = minio.password,
                    autoCreateBucket = autoCreateBucket,
                )
            val config = FileStorageConfig()
            val client: S3Client = config.s3Client(properties)
            return S3FileStorageAdapter(client, config.s3Presigner(properties), properties)
        }
    }
}
