package team.inreok.getiserver.domain.inquiry.dto

import io.swagger.v3.oas.annotations.media.Schema
import team.inreok.getiserver.domain.inquiry.entity.type.InquiryStatus
import java.time.LocalDateTime

/**
 * 답변 등록 결과다(요구사항 §38). 답변 등록은 문의 상태를 항상 [InquiryStatus.ANSWERED]로
 * 자동 전이한다(사용자 확인 완료) -- 담당자 지정과 달리 답변은 문의의 진행 상태 자체를 나타내는
 * 사건이기 때문이다. `inquiryStatus`는 그 결과 값이다.
 */
@Schema(description = "답변 등록 결과")
data class InquiryAnswerCreateResponse(
    @param:Schema(description = "생성된 답변 ID", example = "1")
    val answerId: Long,
    @param:Schema(description = "문의 ID", example = "1")
    val inquiryId: Long,
    @param:Schema(description = "답변 작성자(개발자) 회원 ID", example = "5")
    val authorMemberId: Long,
    @param:Schema(description = "답변 내용", example = "확인 결과 서버 오류로 확인되어 수정했습니다.")
    val content: String,
    @param:Schema(description = "답변 첨부파일 목록(연결한 파일이 없으면 빈 배열)")
    val files: List<InquiryFileResponse>,
    @param:Schema(description = "답변 등록 시각")
    val createdAt: LocalDateTime,
    @param:Schema(description = "답변 등록 후 문의 상태(항상 ANSWERED)", example = "ANSWERED")
    val inquiryStatus: InquiryStatus,
    @param:Schema(
        description =
            "인앱 알림 생성 요청이 접수됐는지 여부다. 알림 Row가 실제로 생성됐는지가 아니라 " +
                "Notification 도메인에 알림 생성 Event를 발행하는 데 성공했는지를 뜻한다 -- 실제 " +
                "생성은 이 Transaction commit 이후 별도 Listener가 비동기로 수행하므로, 이 응답 " +
                "시점에는 아직 알림 Row 존재 여부를 알 수 없다.",
        example = "true",
    )
    val notificationCreated: Boolean,
)
