package team.inreok.getiserver.domain.job.dto

import io.swagger.v3.oas.annotations.media.Schema

/**
 * 공고에 첨부된 파일 Metadata다(Issue #126, `ProgramFileResponse`와 동일한 형태).
 *
 * [downloadUrl]은 File Storage의 실제 URL이 아니라 GETI 자체 다운로드 API 경로
 * (`GET /api/v1/files/{fileId}/download`)다. 그 API가 호출 시점에 권한을 다시 검증하고 짧은
 * 유효기간의 Storage URL로 302 Redirect하므로 이 목록 응답 시점에 미리 Presigned URL을 만들어
 * 담지 않는다.
 */
@Schema(description = "첨부파일 정보")
data class JobFileResponse(
    @param:Schema(description = "파일 ID", example = "1")
    val fileId: Long,
    @param:Schema(description = "원본 파일 이름", example = "채용공고.pdf")
    val originalName: String,
    @param:Schema(description = "MIME Type(서버가 파일 내용에서 탐지한 값)", example = "application/pdf")
    val contentType: String,
    @param:Schema(description = "파일 크기(byte)", example = "10240")
    val size: Long,
    @param:Schema(
        description = "다운로드 API 경로. 호출하면 권한 재검증 후 저장소 URL로 302 Redirect된다.",
        example = "/api/v1/files/1/download",
    )
    val downloadUrl: String,
)
