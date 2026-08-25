package team.inreok.getiserver.domain.file.storage

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * Object Storage 접속 설정이다. Profile별로 값만 달라지고 Adapter 구현은 하나다.
 *
 * - local: MinIO. [endpoint]를 채우고 [pathStyleAccess]=true, [accessKey]/[secretKey]를 준다.
 * - 운영: AWS S3. [endpoint]를 비워 기본 Endpoint를 쓰고 [pathStyleAccess]=false,
 *   [accessKey]/[secretKey]를 **주지 않아** `DefaultCredentialsProvider`(EC2 IAM Role)가
 *   동작하게 한다.
 *
 * 실제 Secret 값은 어떤 yaml에도 쓰지 않는다. 운영은 IAM Role을 쓰므로 Access Key 자체가
 * 존재하지 않는다(지시서 §33).
 */
@ConfigurationProperties(prefix = "app.file.storage")
data class FileStorageProperties(
    /** Bucket 이름. 운영에서는 기본값 없이 환경 변수로만 주입해 미설정 시 기동을 거부한다. */
    val bucket: String,
    val region: String,
    /**
     * `S3Client`(PutObject/GetObject/DeleteObject 등 서버 내부 호출)가 쓰는 Endpoint. 비어 있으면
     * AWS 기본 Endpoint를 쓴다(운영). `docker compose --profile app`처럼 앱이 Container 안에서
     * 도는 환경에서는 Compose Service 이름(`http://minio:9000`)을 쓴다 -- 이 값은 Container
     * 내부에서만 해석되는 이름이라 [publicEndpoint]와 분리해야 한다.
     */
    val endpoint: String? = null,
    /**
     * `S3Presigner`(Presigned URL 서명)가 쓰는 Endpoint. 비어 있으면 [endpoint]를 그대로 쓴다.
     *
     * Presigned URL은 서버가 아니라 **외부 Client(Browser 등)** 가 직접 접속해야 하므로, 앱이
     * Container 안에서 도는 환경(`endpoint=http://minio:9000`)에서는 Client가 해석할 수 없는
     * 내부 DNS 이름이 그대로 URL에 남아 다운로드가 실패한다. 개발자가 `docker compose --profile
     * app`(App까지 Container로 실행)과 `next dev`(Client는 Host에서 실행)를 함께 쓰는 로컬
     * 구성에서는 이 값을 `http://localhost:${MINIO_API_PORT:-9000}`로 분리해 해결한다. 운영
     * 환경([FileStorageProperties]의 `endpoint`가 비어 AWS 기본 Endpoint를 쓰는 경우)은 원래
     * Client가 도달 가능한 Endpoint라 이 값이 필요 없다.
     */
    val publicEndpoint: String? = null,
    /** MinIO는 Path Style(`http://host/bucket/key`)이 필요하다. AWS S3는 false. */
    val pathStyleAccess: Boolean = false,
    val accessKey: String? = null,
    val secretKey: String? = null,
    /** Presigned URL 유효 시간. 짧게 두어야 URL이 유출돼도 노출 창이 좁다(명세 §17 잠정값). */
    val presignedUrlTtl: Duration = Duration.ofMinutes(DEFAULT_PRESIGNED_URL_TTL_MINUTES),
    /**
     * 기동 시 Bucket이 없으면 만들지 여부. **local(MinIO) 전용**이다. 운영에서 앱이 Bucket을
     * 만들면 Block Public Access·암호화 같은 정책 없이 생성될 수 있어 기본값은 false다.
     */
    val autoCreateBucket: Boolean = false,
) {
    init {
        require(bucket.isNotBlank()) { "app.file.storage.bucket은 비어 있을 수 없습니다." }
        require(region.isNotBlank()) { "app.file.storage.region은 비어 있을 수 없습니다." }
        require(!presignedUrlTtl.isZero && !presignedUrlTtl.isNegative) {
            "app.file.storage.presigned-url-ttl은 0보다 커야 합니다. (현재=$presignedUrlTtl)"
        }
        // 한쪽만 주면 Static도 Default도 아닌 어중간한 상태가 된다. 조용히 Default로 넘어가면
        // local에서 "왜 MinIO 인증이 안 되지"를 한참 헤매게 되므로 기동 시점에 막는다.
        require(accessKey.isNullOrBlank() == secretKey.isNullOrBlank()) {
            "app.file.storage의 access-key와 secret-key는 함께 설정하거나 함께 비워야 합니다."
        }
    }

    /** 자격증명을 명시했는지. false면 `DefaultCredentialsProvider`(운영 IAM Role)를 쓴다. */
    fun hasStaticCredentials(): Boolean = !accessKey.isNullOrBlank() && !secretKey.isNullOrBlank()

    /** `S3Presigner`가 실제로 쓸 Endpoint. [publicEndpoint]가 없으면 [endpoint]로 대체한다. */
    fun resolvedPublicEndpoint(): String? = publicEndpoint?.takeIf { it.isNotBlank() } ?: endpoint

    companion object {
        /** 명세 §17의 DECISION_REQUIRED 잠정값. 확정되면 설정으로 덮어쓴다. */
        const val DEFAULT_PRESIGNED_URL_TTL_MINUTES = 15L
    }
}
