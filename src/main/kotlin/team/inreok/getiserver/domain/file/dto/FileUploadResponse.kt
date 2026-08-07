package team.inreok.getiserver.domain.file.dto

import io.swagger.v3.oas.annotations.media.Schema
import team.inreok.getiserver.domain.file.entity.StoredFile
import team.inreok.getiserver.domain.file.entity.type.FilePurpose
import java.time.LocalDateTime

/**
 * 업로드 결과다. `objectKey`, `bucket`, `status`, `uploaderMemberId`는 **의도적으로 넣지
 * 않는다** -- Storage 내부 경로를 외부에 노출하지 않는다는 요구사항 §17/§22 때문이다.
 */
@Schema(description = "파일 업로드 결과")
data class FileUploadResponse(
    @param:Schema(description = "업로드된 파일 ID. 다른 리소스에 첨부할 때 이 값을 사용한다.", example = "42")
    val fileId: Long,
    @param:Schema(description = "정규화된 원본 파일 이름(표시용)", example = "이력서.pdf")
    val originalName: String,
    @param:Schema(
        description = "서버가 파일 내용에서 실제로 탐지한 MIME Type. 클라이언트가 보낸 Content-Type이 아니다.",
        example = "application/pdf",
    )
    val contentType: String,
    @param:Schema(description = "파일 크기(바이트)", example = "102400")
    val size: Long,
    @param:Schema(description = "업로드 목적", example = "JOB_APPLICATION")
    val purpose: FilePurpose,
    @param:Schema(description = "업로드 시각")
    val createdAt: LocalDateTime,
) {
    companion object {
        fun from(file: StoredFile): FileUploadResponse =
            FileUploadResponse(
                fileId = requireNotNull(file.id) { "저장된 파일은 ID를 가져야 합니다." },
                originalName = file.originalName,
                contentType = file.contentType,
                size = file.sizeBytes,
                purpose = file.purpose,
                createdAt = requireNotNull(file.createdAt) { "저장된 파일은 createdAt을 가져야 합니다." },
            )
    }
}
