package team.inreok.getiserver.domain.file.storage

import java.io.InputStream
import java.net.URI
import java.time.Duration

/**
 * Object Storage 접근 계약이다. Application 계층은 이 Interface만 알고 `S3Client`,
 * `PutObjectRequest` 같은 SDK Type을 직접 참조하지 않는다(지시서 §2) -- 그래야 MinIO와 AWS S3를
 * 같은 코드로 다루고 Test에서 Fake로 대체할 수 있다.
 *
 * 이 Interface는 `domain.file` 안에서만 쓴다. 다른 Domain에는 공개하지 않는다 -- Storage Key와
 * Bucket을 아는 것 자체가 File 도메인의 책임이기 때문이다(지시서 §22/§42).
 */
interface FileStoragePort {
    /**
     * Object를 저장한다. [inputStream]은 호출자가 닫는다.
     *
     * [size]를 함께 받는 이유는 SDK가 Content-Length를 알아야 파일 전체를 메모리에 버퍼링하지
     * 않고 스트리밍할 수 있기 때문이다(지시서 §42).
     */
    fun upload(
        key: String,
        contentType: String,
        size: Long,
        inputStream: InputStream,
    )

    /** Object가 존재하는지. DB Metadata와 Storage의 정합성을 확인할 때 쓴다(지시서 §30). */
    fun exists(key: String): Boolean

    /**
     * Object 내용을 읽는다. [FileArchivePort][team.inreok.getiserver.domain.file.archive.FileArchivePort]가
     * 여러 File을 하나의 ZIP으로 합칠 때만 쓴다 -- 그 외에는 항상 [presignedGetUrl]로 Client가
     * Storage에서 직접 받게 하고, 서버가 Body를 대신 읽지 않는다(지시서 §42 "파일 전체를 메모리에
     * 읽어 ... 처리" 금지와 같은 이유로 서버 경유 자체를 최소화한다).
     *
     * 반환한 [InputStream]은 호출자가 닫는다. 존재하지 않는 [key]를 읽으면 `FileStorageException`을
     * 던진다(Metadata와 Storage가 어긋난 상태이므로 "없음"으로 조용히 넘기지 않는다).
     */
    fun download(key: String): InputStream

    /** Object를 지운다. 없는 Key를 지워도 오류가 아니다(보상 처리에서 반복 호출될 수 있다). */
    fun delete(key: String)

    /**
     * 제한 시간 동안만 유효한 다운로드 URL을 만든다.
     *
     * [contentDisposition]과 [contentType]을 Presign Parameter로 함께 서명하므로, Storage가 직접
     * 응답하더라도 서버가 정한 Header가 적용된다. 이것이 원본 파일명 Encoding(§28 Header
     * Injection 방지)과 위험한 Content-Type 강등(§27)을 Presigned URL 방식에서도 유지하는
     * 방법이다.
     */
    fun presignedGetUrl(
        key: String,
        contentDisposition: String,
        contentType: String,
        ttl: Duration,
    ): URI
}
