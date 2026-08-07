package team.inreok.getiserver.domain.file.link

import org.springframework.modulith.NamedInterface

/**
 * 다른 Domain에 공개하는 파일 Metadata다(요구사항 §22).
 *
 * `objectKey`, Bucket, 업로더, `StoredFile` Entity, Repository, S3 SDK 객체는 **의도적으로
 * 넣지 않았다**. Storage 경로를 아는 것은 File 도메인의 책임이며, 다른 Domain이 그것을 알면
 * 결국 Storage에 직접 접근하는 코드가 생긴다.
 */
@NamedInterface
data class FileSnapshot(
    val fileId: Long,
    /** 정규화된 표시용 파일 이름. */
    val originalName: String,
    /** 서버가 파일 내용에서 탐지한 MIME Type. */
    val contentType: String,
    val size: Long,
)
