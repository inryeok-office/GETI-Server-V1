package team.inreok.getiserver.domain.file.storage

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import java.net.URI

/**
 * AWS SDK Client를 구성한다. [S3Client]와 [S3Presigner]에 Path Style 설정은 동일하게 적용해야
 * Presigned URL이 실제 Endpoint와 어긋나지 않는다(MinIO는 Path Style, AWS S3는 Virtual Hosted
 * Style이라 이 값이 다르면 서명은 되는데 접속이 안 되는 형태로 실패한다).
 *
 * 다만 Endpoint 자체는 둘이 다를 수 있다. [S3Client]는 서버가 직접 호출하므로
 * [FileStorageProperties.endpoint]를 쓰고, [S3Presigner]는 만든 URL을 외부 Client가 접속해야
 * 하므로 [FileStorageProperties.resolvedPublicEndpoint]를 쓴다(`docker compose --profile app`
 * 환경에서 `endpoint=http://minio:9000`처럼 Container 내부에서만 해석되는 이름을 그대로
 * Presigned URL에 남기지 않기 위함).
 *
 * SDK Type이 등장하는 곳은 이 Configuration과 [S3FileStorageAdapter]뿐이다. Application 계층은
 * [FileStoragePort]만 본다(지시서 §2).
 */
@Configuration
@EnableConfigurationProperties(FileStorageProperties::class)
class FileStorageConfig {
    @Bean
    fun s3Client(properties: FileStorageProperties): S3Client =
        S3Client
            .builder()
            .region(Region.of(properties.region))
            .credentialsProvider(credentialsProvider(properties))
            .serviceConfiguration(
                S3Configuration
                    .builder()
                    .pathStyleAccessEnabled(properties.pathStyleAccess)
                    .build(),
            ).apply { properties.endpoint?.takeIf { it.isNotBlank() }?.let { endpointOverride(URI.create(it)) } }
            .build()

    @Bean
    fun s3Presigner(properties: FileStorageProperties): S3Presigner =
        S3Presigner
            .builder()
            .region(Region.of(properties.region))
            .credentialsProvider(credentialsProvider(properties))
            .serviceConfiguration(
                S3Configuration
                    .builder()
                    .pathStyleAccessEnabled(properties.pathStyleAccess)
                    .build(),
            ).apply {
                properties.resolvedPublicEndpoint()?.takeIf { it.isNotBlank() }?.let {
                    endpointOverride(
                        URI.create(it),
                    )
                }
            }.build()

    /**
     * 자격증명을 명시했으면 Static(local MinIO), 비웠으면 Default를 쓴다.
     *
     * 운영은 Default 경로다 -- EC2 Instance Profile에서 IMDS로 자격증명을 받아오므로 장기 Access
     * Key를 서버나 Compose 어디에도 두지 않는다. 단, 앱이 Container 안에서 돌기 때문에 EC2의
     * Metadata hop limit이 2 이상이어야 IMDS에 닿는다(기본값 1이면 모든 S3 호출이 실패한다,
     * 명세 §14.3).
     */
    private fun credentialsProvider(properties: FileStorageProperties): AwsCredentialsProvider =
        if (properties.hasStaticCredentials()) {
            StaticCredentialsProvider.create(
                AwsBasicCredentials.create(properties.accessKey, properties.secretKey),
            )
        } else {
            DefaultCredentialsProvider.builder().build()
        }
}
