package team.inreok.getiserver.domain.application.dto

import io.swagger.v3.oas.annotations.media.Schema
import team.inreok.getiserver.domain.application.entity.type.JobApplicationStatus
import java.time.LocalDateTime

// InquiryAdminListResponse/FormListResponse와 동일한 관례를 따른다(global.web.PageResponse는
// 아직 어떤 Domain도 실제로 쓰지 않고, 각 Domain이 전용 Pagination 응답 DTO를 직접 만드는 관례를
// 확립했다).
@Schema(description = "교사·개발자용 지원서 목록 결과. Spring Data Page 규약과 동일하게 page는 0부터 시작한다.")
data class JobApplicationAdminListResponse(
    @param:Schema(description = "조회 결과 목록")
    val content: List<JobApplicationAdminListItemResponse>,
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

@Schema(description = "교사·개발자용 지원서 목록 항목")
data class JobApplicationAdminListItemResponse(
    @param:Schema(description = "지원서 ID", example = "1")
    val applicationId: Long,
    @param:Schema(description = "공고 ID", example = "1")
    val jobId: Long,
    @param:Schema(description = "지원자 회원 ID", example = "1")
    val applicantMemberId: Long,
    @param:Schema(description = "지원자 이름 스냅샷", nullable = true)
    val applicantName: String?,
    @param:Schema(description = "지원자 기수 스냅샷", nullable = true)
    val applicantCohort: Int?,
    @param:Schema(description = "지원자 학과 스냅샷", nullable = true)
    val applicantDepartment: String?,
    @param:Schema(description = "공고명. 공고가 삭제되어 조회할 수 없으면 null.", nullable = true)
    val jobTitle: String?,
    @param:Schema(description = "기업명. 공고 또는 기업을 조회할 수 없으면 null.", nullable = true)
    val companyName: String?,
    @param:Schema(description = "담당 교사 회원 ID. 공고에 담당자가 지정되지 않았으면 null.", nullable = true)
    val managerMemberId: Long?,
    @param:Schema(description = "담당 교사 이름. 담당자가 없거나 조회할 수 없으면 null.", nullable = true)
    val managerName: String?,
    @param:Schema(description = "지원 상태", example = "SUBMITTED")
    val status: JobApplicationStatus,
    @param:Schema(description = "제출 일시. 아직 제출 전(DRAFT)이면 null.", nullable = true)
    val submittedAt: LocalDateTime?,
    @param:Schema(description = "생성 일시")
    val createdAt: LocalDateTime,
)
