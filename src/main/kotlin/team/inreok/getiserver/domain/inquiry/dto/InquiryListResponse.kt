package team.inreok.getiserver.domain.inquiry.dto

import io.swagger.v3.oas.annotations.media.Schema
import team.inreok.getiserver.domain.inquiry.entity.type.InquiryStatus
import team.inreok.getiserver.domain.inquiry.entity.type.InquiryType
import java.time.LocalDateTime

// global.web.PageResponse는 아직 어떤 Domain도 실제로 쓰지 않고(Program/Member Search가 이미
// Domain 전용 Pagination 응답 DTO를 직접 만드는 관례를 확립했다), 그 관례를 그대로 따랐다.
@Schema(description = "내 문의 목록 결과. Spring Data Page 규약과 동일하게 page는 0부터 시작한다.")
data class InquiryListResponse(
    @param:Schema(description = "조회 결과 목록")
    val content: List<InquiryListItemResponse>,
    @param:Schema(description = "현재 Page 번호(0부터 시작)", example = "0")
    val page: Int,
    @param:Schema(description = "Page당 개수", example = "20")
    val size: Int,
    @param:Schema(description = "전체 결과 개수", example = "3")
    val totalElements: Long,
    @param:Schema(description = "전체 Page 수", example = "1")
    val totalPages: Int,
    @param:Schema(description = "첫 Page 여부", example = "true")
    val first: Boolean,
    @param:Schema(description = "마지막 Page 여부", example = "true")
    val last: Boolean,
)

@Schema(description = "내 문의 목록 항목")
data class InquiryListItemResponse(
    @param:Schema(description = "문의 ID", example = "1")
    val inquiryId: Long,
    @param:Schema(description = "문의 유형", example = "ERROR")
    val inquiryType: InquiryType,
    @param:Schema(description = "문의 제목", example = "로그인이 안 됩니다")
    val title: String,
    @param:Schema(description = "문의 상태", example = "ANSWERED")
    val status: InquiryStatus,
    @param:Schema(description = "등록 시각")
    val createdAt: LocalDateTime,
    @param:Schema(description = "최초 답변 시각. 아직 답변이 없으면 null", nullable = true)
    val answeredAt: LocalDateTime?,
)
