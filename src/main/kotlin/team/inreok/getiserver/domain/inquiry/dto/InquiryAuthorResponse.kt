package team.inreok.getiserver.domain.inquiry.dto

import io.swagger.v3.oas.annotations.media.Schema

/**
 * 문의 작성자 정보다(요구사항 5절/17절/18절). `department`는
 * [team.inreok.getiserver.domain.member.query.InquiryMemberSnapshot]이 원시 값(`DepartmentType.name`)만
 * 돌려주므로 Inquiry Module이 `member`의 실제 Enum Type을 참조하지 않도록 그대로 String으로 둔다.
 */
@Schema(description = "문의 작성자 정보")
data class InquiryAuthorResponse(
    @param:Schema(description = "작성자 회원 ID", example = "1")
    val memberId: Long,
    @param:Schema(description = "작성자 이름", example = "홍길동", nullable = true)
    val name: String?,
    @param:Schema(description = "프로필 이미지 URL. 이미지가 없거나 접근 권한이 없으면 null", nullable = true)
    val profileImageUrl: String?,
    @param:Schema(description = "기수", example = "10", nullable = true)
    val cohort: Int?,
    @param:Schema(description = "학과(DepartmentType)", example = "SW_DEVELOPMENT", nullable = true)
    val department: String?,
    @param:Schema(description = "프로필 공개 여부", example = "true")
    val isPublic: Boolean,
)
