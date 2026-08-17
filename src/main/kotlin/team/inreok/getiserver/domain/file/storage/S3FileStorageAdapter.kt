package team.inreok.getiserver.domain.file.storage

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import software.amazon.awssdk.core.exception.SdkException
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.CreateBucketRequest
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.NoSuchBucketException
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.S3Exception
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import team.inreok.getiserver.domain.file.exception.FileStorageException
import java.io.InputStream
import java.net.URI
import java.time.Duration

/**
 * S3 Compatible Object Storage Adapter다. local은 MinIO, 운영은 AWS S3지만 구현은 하나이며
 * Endpoint와 Path Style만 설정으로 달라진다([FileStorageConfig] 참고).
 *
 * SDK 예외는 여기서 모두 [FileStorageException]으로 감싼다. SDK의 Message에는 Bucket 이름,
 * Request ID, 내부 Endpoint가 들어 있어 사용자 응답에 나가면 안 되기 때문이다(지시서 §26/§42).
 * 원인은 로그에만 남긴다.
 */
@Component
class S3FileStorageAdapter(
    private val s3Client: S3Client,
    private val s3Presigner: S3Presigner,
    private val properties: FileStorageProperties,
) : FileStoragePort {
    private val log = LoggerFactory.getLogger(javaClass)

    init {
        if (properties.autoCreateBucket) {
            createBucketIfAbsent()
        }
    }

    override fun upload(
        key: String,
        contentType: String,
        size: Long,
        inputStream: InputStream,
    ) {
        val request =
            PutObjectRequest
                .builder()
                .bucket(properties.bucket)
                .key(key)
                .contentType(contentType)
                .contentLength(size)
                .build()
        try {
            // contentLength를 함께 주면 SDK가 파일 전체를 메모리에 버퍼링하지 않고 스트리밍한다
            // (지시서 §42 "파일 전체를 메모리에 읽어 Upload 처리" 금지).
            s3Client.putObject(request, RequestBody.fromInputStream(inputStream, size))
        } catch (ex: SdkException) {
            log.error("Object Storage 업로드에 실패했습니다. key={}, size={}", key, size, ex)
            throw FileStorageException(operation = "upload", cause = ex)
        }
    }

    override fun exists(key: String): Boolean {
        val request =
            HeadObjectRequest
                .builder()
                .bucket(properties.bucket)
                .key(key)
                .build()
        return try {
            s3Client.headObject(request)
            true
        } catch (ex: NoSuchKeyException) {
            // 정상적인 "없음" 응답이다. 로그를 남기지 않는다.
            log.debug("Object가 존재하지 않습니다. key={}", key, ex)
            false
        } catch (ex: S3Exception) {
            // MinIO/S3는 HeadObject에 404를 그대로 주기도 해서 NoSuchKeyException으로 매핑되지
            // 않는 경우가 있다. 404만 "없음"으로 보고 나머지(권한, 네트워크)는 장애로 올린다.
            if (ex.statusCode() == HTTP_NOT_FOUND) {
                log.debug("Object가 존재하지 않습니다(404). key={}", key, ex)
                false
            } else {
                log.error("Object 존재 확인에 실패했습니다. key={}", key, ex)
                throw FileStorageException(operation = "exists", cause = ex)
            }
        }
    }

    override fun download(key: String): InputStream {
        val request =
            GetObjectRequest
                .builder()
                .bucket(properties.bucket)
                .key(key)
                .build()
        return try {
            // ResponseInputStream은 Body를 lazy하게 스트리밍한다 -- 여기서 전체를 읽어 메모리에
            // 올리지 않는다(지시서 §42). 호출자가 닫아야 실제 HTTP Connection이 반환된다.
            s3Client.getObject(request)
        } catch (ex: NoSuchKeyException) {
            log.error("Object가 존재하지 않습니다(Metadata와 Storage 불일치). key={}", key, ex)
            throw FileStorageException(operation = "download", cause = ex)
        } catch (ex: SdkException) {
            log.error("Object Storage 다운로드에 실패했습니다. key={}", key, ex)
            throw FileStorageException(operation = "download", cause = ex)
        }
    }

    override fun delete(key: String) {
        val request =
            DeleteObjectRequest
                .builder()
                .bucket(properties.bucket)
                .key(key)
                .build()
        try {
            // S3의 DeleteObject는 없는 Key에도 성공을 돌려준다(멱등). 보상 처리에서 여러 번
            // 호출돼도 안전하다.
            s3Client.deleteObject(request)
        } catch (ex: SdkException) {
            log.error("Object Storage 삭제에 실패했습니다. key={}", key, ex)
            throw FileStorageException(operation = "delete", cause = ex)
        }
    }

    override fun presignedGetUrl(
        key: String,
        contentDisposition: String,
        contentType: String,
        ttl: Duration,
    ): URI {
        // Content-Disposition과 Content-Type을 Presign에 함께 서명한다. 이렇게 해야 Storage가
        // 직접 응답해도 서버가 정한 파일명과 처리 방식이 적용되고, 클라이언트가 URL의 Query를
        // 고쳐 다른 값을 넣으면 서명이 깨져 거부된다(지시서 §27/§28).
        val getObjectRequest =
            GetObjectRequest
                .builder()
                .bucket(properties.bucket)
                .key(key)
                .responseContentDisposition(contentDisposition)
                .responseContentType(contentType)
                .build()
        val presignRequest =
            GetObjectPresignRequest
                .builder()
                .signatureDuration(ttl)
                .getObjectRequest(getObjectRequest)
                .build()
        return try {
            s3Presigner.presignGetObject(presignRequest).url().toURI()
        } catch (ex: SdkException) {
            log.error("Presigned URL 생성에 실패했습니다. key={}", key, ex)
            throw FileStorageException(operation = "presign", cause = ex)
        }
    }

    /**
     * local(MinIO)에서 개발자가 Bucket을 수동으로 만들지 않아도 되게 한다. 운영에서는
     * `auto-create-bucket`이 false라 실행되지 않는다 -- Block Public Access와 암호화 설정이
     * 빠진 Bucket이 앱에 의해 생기면 안 되기 때문이다.
     */
    private fun createBucketIfAbsent() {
        when (bucketExistence()) {
            BucketExistence.ABSENT -> createBucket()

            // 이미 있거나(PRESENT) 확인 자체가 실패한(UNKNOWN) 경우에는 아무것도 하지 않는다.
            // UNKNOWN에서 생성을 시도하면 권한 오류를 Bucket 부재로 오인해 매 기동마다 실패한다.
            BucketExistence.PRESENT, BucketExistence.UNKNOWN -> Unit
        }
    }

    private fun bucketExistence(): BucketExistence =
        try {
            s3Client.headBucket { it.bucket(properties.bucket) }
            BucketExistence.PRESENT
        } catch (ex: NoSuchBucketException) {
            log.debug("Bucket이 존재하지 않습니다. bucket={}", properties.bucket, ex)
            BucketExistence.ABSENT
        } catch (ex: S3Exception) {
            // MinIO는 HeadBucket에 NoSuchBucketException 대신 404를 그대로 주기도 한다.
            if (ex.statusCode() == HTTP_NOT_FOUND) {
                log.debug("Bucket이 존재하지 않습니다(404). bucket={}", properties.bucket, ex)
                BucketExistence.ABSENT
            } else {
                log.warn("Bucket 존재 확인에 실패했습니다. bucket={}", properties.bucket, ex)
                BucketExistence.UNKNOWN
            }
        } catch (ex: SdkException) {
            // Storage가 아직 뜨지 않았을 수 있다. 기동 자체를 막지는 않고 실제 업로드 시점에
            // 실패하게 둔다(Collector/Discord가 외부 의존성을 Fail-Fast로 다루지 않는 것과 동일).
            log.warn("Bucket 존재 확인에 실패했습니다. bucket={}", properties.bucket, ex)
            BucketExistence.UNKNOWN
        }

    private fun createBucket() {
        try {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(properties.bucket).build())
            log.info("Bucket을 새로 만들었습니다(local 전용). bucket={}", properties.bucket)
        } catch (ex: SdkException) {
            log.warn("Bucket 생성에 실패했습니다. bucket={}", properties.bucket, ex)
        }
    }

    private enum class BucketExistence { PRESENT, ABSENT, UNKNOWN }

    private companion object {
        private const val HTTP_NOT_FOUND = 404
    }
}
