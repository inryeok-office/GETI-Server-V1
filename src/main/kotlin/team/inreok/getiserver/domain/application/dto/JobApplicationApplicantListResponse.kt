package team.inreok.getiserver.domain.application.dto

import io.swagger.v3.oas.annotations.media.Schema

// JobApplicationAdminListResponse와 동일한 관례를 따른다(global.web.PageResponse는 아직 어떤
// Domain도 실제로 쓰지 않고, 각 Domain이 전용 Pagination 응답 DTO를 직접 만드는 관례를 확립했다).
@Schema(description = "공고별 지원자 목록 결과(Issue #137). Spring Data Page 규약과 동일하게 page는 0부터 시작한다.")
data class JobApplicationApplicantListResponse(
    @param:Schema(description = "조회 결과 목록")
    val content: List<JobApplicationApplicantResponse>,
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

/**
 * 공고별 지원자 목록 항목 하나다. `applicationId`는 개인정보가 아니라 이 지원자의 상세 조회
 * (`GET /api/v1/admin/job-applications/{applicationId}`)로 이어가기 위한 식별자라 그대로 노출한다.
 * 그 외 필드는 Issue #137 정책 확정("최소 정보만 노출")에 따라 이름·프로필 사진·기수·학과로
 * 한정했다 -- `applicationStatus`는 이 목록의 노출 범위에서 제외됐다(사용자 확인 완료).
 */
@Schema(description = "공고별 지원자 목록 항목")
data class JobApplicationApplicantResponse(
    @param:Schema(description = "지원서 ID(상세 조회에 사용)", example = "1")
    val applicationId: Long,
    @param:Schema(description = "지원자 회원 ID", example = "1")
    val memberId: Long,
    @param:Schema(description = "지원자 이름", example = "홍길동", nullable = true)
    val name: String?,
    @param:Schema(description = "프로필 이미지 URL. 이미지가 없으면 null", nullable = true)
    val profileImageUrl: String?,
    @param:Schema(description = "기수", example = "10", nullable = true)
    val cohort: Int?,
    @param:Schema(description = "학과(DepartmentType)", example = "SW_DEVELOPMENT", nullable = true)
    val department: String?,
)
