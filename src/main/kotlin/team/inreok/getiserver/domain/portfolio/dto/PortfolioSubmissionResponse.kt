package team.inreok.getiserver.domain.portfolio.dto

import io.swagger.v3.oas.annotations.media.Schema
import team.inreok.getiserver.domain.file.link.FileSnapshot
import team.inreok.getiserver.domain.portfolio.entity.PortfolioSubmission
import team.inreok.getiserver.domain.portfolio.entity.type.PortfolioSubmissionStatus
import java.time.LocalDateTime

/**
 * 학생 포트폴리오 제출 결과 응답이다(요구사항 §14, Issue #204).
 *
 * 제출자 학생 프로필(name/cohort/department 등)은 이 응답에 넣지 않는다 -- 자기 제출물을 되돌려
 * 받는 학생은 Access Token으로 이미 신원이 확정돼 있어 자기 프로필을 다시 실어 줄 이유가 없다.
 * 학생 프로필이 실제로 필요한 곳은 관리자 제출 현황(요구사항 §19, 후속 Phase)이며 그때 Member
 * Snapshot 계약으로 채운다 -- 여기서 가짜/불필요한 Field를 미리 만들지 않는다.
 */
@Schema(description = "포트폴리오 제출 결과")
data class PortfolioSubmissionResponse(
    @param:Schema(description = "제출물 ID", example = "1")
    val submissionId: Long,
    @param:Schema(description = "수합 요청 ID", example = "1")
    val requestId: Long,
    @param:Schema(description = "제출 상태", example = "SUBMITTED")
    val status: PortfolioSubmissionStatus,
    @param:Schema(description = "포트폴리오 URL", example = "https://portfolio.example.com/me", nullable = true)
    val portfolioUrl: String?,
    @param:Schema(description = "제출 메모", example = "일부 자료는 링크로 대체했습니다.", nullable = true)
    val note: String?,
    @param:Schema(description = "연결된 첨부 파일 목록")
    val files: List<PortfolioSubmissionFileResponse>,
    @param:Schema(description = "제출 확정 시각. 임시저장만 한 경우 null이다.", example = "2026-09-20T10:00:00", nullable = true)
    val submittedAt: LocalDateTime?,
    @param:Schema(description = "수정 시각", example = "2026-09-20T10:00:00")
    val updatedAt: LocalDateTime?,
) {
    companion object {
        fun from(
            submission: PortfolioSubmission,
            files: List<FileSnapshot>,
        ): PortfolioSubmissionResponse =
            PortfolioSubmissionResponse(
                submissionId = requireNotNull(submission.id) { "저장된 PortfolioSubmission은 id를 가져야 합니다." },
                requestId = submission.requestId,
                status = submission.status,
                portfolioUrl = submission.portfolioUrl,
                note = submission.note,
                files = files.map(PortfolioSubmissionFileResponse::from),
                submittedAt = submission.submittedAt,
                updatedAt = submission.updatedAt,
            )
    }
}

/**
 * 제출물에 연결된 첨부 파일 Metadata다.
 *
 * [downloadUrl]은 Storage의 실제 URL이 아니라 GETI 다운로드 API 경로다 -- 그 API가 호출 시점에
 * 권한을 다시 검증하고 짧은 유효기간의 Storage URL로 302 Redirect한다(요구사항 "영구 Public URL
 * 금지", InquiryFileResponse/JobApplicationFileResponse와 같은 관례).
 */
@Schema(description = "제출물 첨부 파일 정보")
data class PortfolioSubmissionFileResponse(
    @param:Schema(description = "파일 ID", example = "1")
    val fileId: Long,
    @param:Schema(description = "원본 파일 이름", example = "portfolio.pdf")
    val originalName: String,
    @param:Schema(description = "MIME Type(서버가 파일 내용에서 탐지한 값)", example = "application/pdf")
    val contentType: String,
    @param:Schema(description = "파일 크기(byte)", example = "102400")
    val size: Long,
    @param:Schema(
        description = "다운로드 API 경로. 호출하면 권한 재검증 후 저장소 URL로 302 Redirect된다.",
        example = "/api/v1/files/1/download",
    )
    val downloadUrl: String,
) {
    companion object {
        fun from(snapshot: FileSnapshot): PortfolioSubmissionFileResponse =
            PortfolioSubmissionFileResponse(
                fileId = snapshot.fileId,
                originalName = snapshot.originalName,
                contentType = snapshot.contentType,
                size = snapshot.size,
                downloadUrl = "/api/v1/files/${snapshot.fileId}/download",
            )
    }
}
