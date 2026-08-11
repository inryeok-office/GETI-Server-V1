package team.inreok.getiserver.domain.inquiry.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

/**
 * 답변 등록 요청이다(요구사항 §38). CLOSED 상태의 문의에는 답변을 등록할 수 없다
 * ([team.inreok.getiserver.domain.inquiry.exception.InquiryAlreadyClosedException], 409).
 */
@Schema(description = "문의 답변 등록 요청")
data class InquiryAnswerCreateRequest(
    @field:NotBlank(message = "답변 내용은 비어 있을 수 없습니다.")
    @param:Schema(description = "답변 내용(필수)", example = "확인 결과 서버 오류로 확인되어 수정했습니다.")
    val content: String,
    @param:Schema(
        description = "첨부파일 ID 목록(선택). FilePurpose=INQUIRY_ANSWER_ATTACHMENT로 업로드하고 본인이 소유한 파일만 연결할 수 있다.",
        example = "[1, 2]",
        nullable = true,
    )
    val fileIds: List<Long>? = null,
)
