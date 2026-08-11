package team.inreok.getiserver.domain.inquiry.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

/**
 * 문의에 달린 답변 하나다(요구사항 20절). 답변 등록 API는 Phase 4에서 구현하지만, 응답 형태
 * 자체는 상세 조회(§17)가 이번 Phase부터 필요로 해 함께 정의한다.
 */
@Schema(description = "답변 정보")
data class InquiryAnswerItemResponse(
    @param:Schema(description = "답변 ID", example = "1")
    val answerId: Long,
    @param:Schema(description = "문의 ID", example = "1")
    val inquiryId: Long,
    @param:Schema(description = "답변 작성자(개발자) 회원 ID", example = "5")
    val authorMemberId: Long,
    @param:Schema(description = "답변 내용", example = "확인 결과 서버 오류로 확인되어 수정했습니다.")
    val content: String,
    @param:Schema(description = "답변 첨부파일 목록")
    val files: List<InquiryFileResponse>,
    @param:Schema(description = "답변 등록 시각")
    val createdAt: LocalDateTime,
)
