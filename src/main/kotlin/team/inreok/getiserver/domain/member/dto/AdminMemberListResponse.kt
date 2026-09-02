package team.inreok.getiserver.domain.member.dto

import io.swagger.v3.oas.annotations.media.Schema

/**
 * 관리자 화면의 담당자 선택 Dropdown 등에서 특정 Role의 회원 목록을 채우기 위한 응답이다(Issue #182).
 *
 * Dropdown에 필요한 최소 정보(`memberId`, `name`)만 담고 email/phone 같은 민감 정보는 노출하지
 * 않는다([team.inreok.getiserver.domain.member.query.InquiryAssigneeCandidate]/`InquiryMemberSnapshot`이
 * 이미 이름만 노출하는 관례와 동일). 교사 수가 학교 규모라 Pagination·이름 검색은 두지 않고 전체를
 * 이름순으로 돌려준다.
 */
@Schema(description = "관리자용 회원 목록(담당자 Dropdown 등)")
data class AdminMemberListResponse(
    @param:Schema(description = "조회된 회원 목록(이름 오름차순)")
    val members: List<AdminMemberSummaryResponse>,
)

@Schema(description = "관리자용 회원 요약")
data class AdminMemberSummaryResponse(
    @param:Schema(description = "회원 ID", example = "42")
    val memberId: Long,
    @param:Schema(description = "회원 이름", example = "김교사", nullable = true)
    val name: String?,
)
