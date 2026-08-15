package team.inreok.getiserver.domain.application.dto

import io.swagger.v3.oas.annotations.media.Schema

/**
 * 지원서에 연결된 첨부파일 정보다(Issue #134). [InquiryFileResponse][team.inreok.getiserver.domain.inquiry.dto.InquiryFileResponse]와
 * 같은 이유로 [downloadUrl]에 File Storage의 실제 URL이 아니라 GETI 자체 다운로드 API 경로
 * (`GET /api/v1/files/{fileId}/download`)를 담는다 -- 그 API가 호출 시점에 권한을 다시 검증하고
 * 짧은 유효기간의 Storage URL로 302 Redirect한다("영구 Public URL 금지").
 */
@Schema(description = "지원서에 연결된 첨부파일 정보")
data class JobApplicationFileResponse(
    @param:Schema(description = "파일 ID", example = "1")
    val fileId: Long,
    @param:Schema(description = "원본 파일 이름", example = "resume.pdf")
    val originalName: String,
    @param:Schema(description = "MIME Type(서버가 파일 내용에서 탐지한 값)", example = "application/pdf")
    val contentType: String,
    @param:Schema(description = "파일 크기(byte)", example = "1024")
    val size: Long,
    @param:Schema(
        description = "다운로드 API 경로. 호출하면 권한 재검증 후 저장소 URL로 302 Redirect된다.",
        example = "/api/v1/files/1/download",
    )
    val downloadUrl: String,
)
