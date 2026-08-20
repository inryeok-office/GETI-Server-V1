package team.inreok.getiserver.domain.application.dto

import io.swagger.v3.oas.annotations.media.Schema
import team.inreok.getiserver.domain.application.entity.type.JobApplicationStatus
import team.inreok.getiserver.domain.company.query.CompanySummary
import team.inreok.getiserver.domain.job.entity.type.ApplicationMethod
import team.inreok.getiserver.domain.job.entity.type.JobStatus
import team.inreok.getiserver.domain.job.entity.type.PostingType
import java.time.LocalDateTime

// JobApplicationAdminListResponse/JobApplicationApplicantListResponse와 동일한 관례를 따른다
// (global.web.PageResponse는 아직 어떤 Domain도 실제로 쓰지 않고, 각 Domain이 전용 Pagination
// 응답 DTO를 직접 만드는 관례를 확립했다).
@Schema(description = "학생 본인 지원 목록 결과(Issue #184). Spring Data Page 규약과 동일하게 page는 0부터 시작한다.")
data class MyJobApplicationListResponse(
    @param:Schema(description = "조회 결과 목록")
    val content: List<MyJobApplicationListItemResponse>,
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

@Schema(description = "학생 본인 지원 목록 항목")
data class MyJobApplicationListItemResponse(
    @param:Schema(description = "지원서 ID", example = "1")
    val applicationId: Long,
    @param:Schema(
        description = "지원 대상 공고 요약. 공고가 삭제되어 조회할 수 없으면 null.",
        nullable = true,
    )
    val job: MyJobApplicationJobSummary?,
    @param:Schema(description = "지원 상태", example = "SUBMITTED")
    val status: JobApplicationStatus,
    @param:Schema(description = "제출 일시. 아직 제출 전(DRAFT)이면 null.", nullable = true)
    val submittedAt: LocalDateTime?,
    @param:Schema(description = "마지막 수정 일시")
    val updatedAt: LocalDateTime,
)

/**
 * 목록 항목에 실을 공고 요약이다. `job.dto.JobSummaryResponse`/`recommendation.dto.RecommendationJobResponse`와
 * 같은 모양이되 별도 Class로 둔다 -- Domain 간 DTO 직접 의존을 만들지 않기 위해서다
 * (`RecommendationJobResponse` KDoc과 같은 이유).
 *
 * Notion Job Summary 계약의 `techStacks`, `bookmarkCount`는 포함하지 않았다 --
 * `RecommendationJobResponse` KDoc과 같은 이유로 실제 뒷받침하는 구현이 없다(Job에 기술스택 Column
 * 없음, 북마크 수를 세는 Query 없음). 가짜 값을 채우지 않고 CONTRACT_MISMATCH로 남겼다.
 */
@Schema(description = "지원 목록 항목에 포함되는 공고 요약 정보")
data class MyJobApplicationJobSummary(
    @param:Schema(description = "공고 ID", example = "1")
    val jobId: Long,
    @param:Schema(description = "공고 제목", example = "2026 상반기 백엔드 채용")
    val title: String,
    @param:Schema(description = "공고 유형", example = "MOU")
    val postingType: PostingType,
    @param:Schema(description = "지원 방식", example = "EXTERNAL")
    val applicationMethod: ApplicationMethod,
    @param:Schema(description = "공고 상태", example = "PUBLISHED")
    val status: JobStatus,
    @param:Schema(
        description = "기업 요약. 공고 등록 후 기업이 삭제되면 null이 될 수 있다.",
        nullable = true,
    )
    val company: CompanySummary?,
    @param:Schema(description = "모집 종료 시각. null이면 마감 없는 공고다.", nullable = true)
    val endDate: LocalDateTime?,
    @param:Schema(description = "조회수", example = "128")
    val viewCount: Long,
    @param:Schema(description = "요청자(본인) 기준 북마크 여부", example = "false")
    val bookmarked: Boolean,
)
