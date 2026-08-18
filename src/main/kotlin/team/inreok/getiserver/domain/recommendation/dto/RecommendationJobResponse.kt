package team.inreok.getiserver.domain.recommendation.dto

import io.swagger.v3.oas.annotations.media.Schema
import team.inreok.getiserver.domain.company.query.CompanySummary
import team.inreok.getiserver.domain.job.entity.type.ApplicationMethod
import team.inreok.getiserver.domain.job.entity.type.JobStatus
import team.inreok.getiserver.domain.job.entity.type.PostingType
import java.time.LocalDateTime

/**
 * 추천/관심 없음 Card에 표시할 Job 정보다(Recommendation R3 Issue #152, Notion 계약 정합성 Issue
 * #155). `job`/`company` Entity를 직접 참조하지 않고
 * `job.query.JobRecommendationCandidateQueryPort`/`company.query.CompanyQuery`가 돌려준 값만
 * 옮겨 담는다 -- `job.dto`/`search.dto`의 기존 Job 응답 Class를 그대로 재사용하지 않는 이유는
 * Domain 간 DTO 직접 의존을 만들지 않기 위해서다.
 *
 * Notion Job Summary 계약의 `source`(sourceId/name/sourceType), `techStacks`,
 * `company.companyType`/`company.mouStatus`, `bookmarkCount`는 이번 PR에 포함하지 않았다 --
 * 실제 Source가 없거나(Job에 별도 Source Entity 없음, `sourceName`은 String Column뿐) 기존
 * 공개 Query Contract가 의도적으로 좁혀 둔 값이라(`CompanySummary`는 CompanyType/MouStatus를
 * 담지 않기로 결정됨) 가짜 값을 채우지 않고 CONTRACT_MISMATCH로 남겼다(PR 본문 Matrix 참고).
 */
@Schema(description = "추천/관심 없음 Card에 표시할 공고 정보")
data class RecommendationJobResponse(
    @param:Schema(description = "공고 ID", example = "1")
    val jobId: Long,
    @param:Schema(description = "공고 제목", example = "백엔드 개발 인턴")
    val title: String,
    @param:Schema(description = "공고 유형", example = "MOU")
    val postingType: PostingType,
    @param:Schema(description = "지원 방식", example = "EXTERNAL")
    val applicationMethod: ApplicationMethod,
    @param:Schema(description = "공고 상태", example = "PUBLISHED")
    val status: JobStatus,
    @param:Schema(
        description = "기업 요약. 기업이 삭제됐거나 조회할 수 없으면 null.",
        nullable = true,
    )
    val company: CompanySummary?,
    @param:Schema(description = "모집 마감 시각. 상시 채용 등 마감일이 없으면 null.", nullable = true)
    val endDate: LocalDateTime?,
    @param:Schema(description = "조회수", example = "128")
    val viewCount: Long,
    @param:Schema(description = "요청자 본인이 이 공고를 북마크했는지 여부", example = "false")
    val bookmarked: Boolean,
)
