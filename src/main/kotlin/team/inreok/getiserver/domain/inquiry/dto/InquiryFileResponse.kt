package team.inreok.getiserver.domain.inquiry.dto

import io.swagger.v3.oas.annotations.media.Schema

/**
 * 문의·답변에 첨부된 파일 Metadata다(요구사항 5절/17절/44절).
 *
 * [downloadUrl]은 File Storage의 실제 URL이 아니라 GETI 자체 다운로드 API 경로
 * (`GET /api/v1/files/{fileId}/download`)다. 그 API가 호출 시점에 권한을 다시 검증하고 짧은
 * 유효기간의 Storage URL로 302 Redirect하므로(요구사항 44절 "영구 Public URL 금지"), 이 목록
 * 응답 시점에 미리 Presigned URL을 만들어 담지 않는다 -- 이미지가 아닌 문서(PDF 등)에 대한
 * Presigned URL 발급 계약은 아직 File 도메인에 없고([team.inreok.getiserver.domain.file.link.FileUrlPort]는
 * 이미지 전용이다), 만들더라도 목록을 만든 시점과 실제로 클릭하는 시점 사이에 만료될 수 있다.
 */
@Schema(description = "첨부파일 정보")
data class InquiryFileResponse(
    @param:Schema(description = "파일 ID", example = "1")
    val fileId: Long,
    @param:Schema(description = "원본 파일 이름", example = "error.png")
    val originalName: String,
    @param:Schema(description = "MIME Type(서버가 파일 내용에서 탐지한 값)", example = "image/png")
    val contentType: String,
    @param:Schema(description = "파일 크기(byte)", example = "1024")
    val size: Long,
    @param:Schema(
        description = "다운로드 API 경로. 호출하면 권한 재검증 후 저장소 URL로 302 Redirect된다.",
        example = "/api/v1/files/1/download",
        nullable = true,
    )
    val downloadUrl: String?,
)
