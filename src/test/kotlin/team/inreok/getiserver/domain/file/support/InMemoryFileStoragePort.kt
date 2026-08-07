package team.inreok.getiserver.domain.file.support

import team.inreok.getiserver.domain.file.exception.FileStorageException
import team.inreok.getiserver.domain.file.storage.FileStoragePort
import java.io.InputStream
import java.net.URI
import java.time.Duration

/**
 * Docker 없이 도는 Unit Test용 Storage Fake다(`src/test`는 Docker 없이 통과해야 한다).
 * 실제 S3/MinIO 동작(서명, path-style, Content-Disposition override)은 `src/integrationTest`가
 * Testcontainers MinIO로 검증한다.
 */
class InMemoryFileStoragePort : FileStoragePort {
    private val objects = mutableMapOf<String, ByteArray>()

    /** 보상 처리 검증용. 삭제가 실제로 호출됐는지 본다. */
    val deleted = mutableListOf<String>()

    var failOnUpload = false
    var failOnDelete = false
    var failOnPresign = false

    override fun upload(
        key: String,
        contentType: String,
        size: Long,
        inputStream: InputStream,
    ) {
        if (failOnUpload) throw FileStorageException(operation = "upload")
        objects[key] = inputStream.readBytes()
    }

    override fun exists(key: String): Boolean = objects.containsKey(key)

    override fun delete(key: String) {
        deleted += key
        if (failOnDelete) throw FileStorageException(operation = "delete")
        objects.remove(key)
    }

    override fun presignedGetUrl(
        key: String,
        contentDisposition: String,
        contentType: String,
        ttl: Duration,
    ): URI {
        if (failOnPresign) throw FileStorageException(operation = "presign")
        // 실제 서명은 하지 않는다. Test는 "권한 검증을 통과해야 URL이 나온다"와 "어떤 값으로
        // 서명을 요청했는가"만 확인하면 된다.
        return URI.create("https://storage.test/$key?disposition=$contentDisposition&ttl=${ttl.seconds}")
    }

    fun contains(key: String): Boolean = objects.containsKey(key)

    fun keys(): Set<String> = objects.keys.toSet()
}
