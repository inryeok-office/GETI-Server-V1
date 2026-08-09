package team.inreok.getiserver.domain.inquiry.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import team.inreok.getiserver.domain.inquiry.entity.type.InquiryType

/**
 * 문의 등록 요청이다(요구사항 5절). `status`/`assigneeId`/Discord 상태는 클라이언트가 지정할 수
 * 없다 -- 서버가 [team.inreok.getiserver.domain.inquiry.entity.type.InquiryStatus.RECEIVED]로
 * 고정한다(요구사항 4절).
 */
@Schema(description = "문의 등록 요청")
data class InquiryCreateRequest(
    @param:Schema(description = "문의 유형(필수)", example = "ERROR")
    val inquiryType: InquiryType,
    @field:NotBlank(message = "문의 제목은 비어 있을 수 없습니다.")
    @field:Size(max = 500, message = "문의 제목은 500자를 넘을 수 없습니다.")
    @param:Schema(description = "문의 제목(필수, 최대 500자)", example = "로그인이 안 됩니다", maxLength = 500)
    val title: String,
    @field:NotBlank(message = "문의 내용은 비어 있을 수 없습니다.")
    @param:Schema(description = "문의 내용(필수)", example = "로그인 버튼을 눌러도 반응이 없습니다.")
    val content: String,
    @param:Schema(
        description = "첨부파일 ID 목록(선택). FilePurpose=INQUIRY_ATTACHMENT로 업로드하고 본인이 소유한 파일만 연결할 수 있다.",
        example = "[1, 2]",
        nullable = true,
    )
    val fileIds: List<Long>? = null,
)
