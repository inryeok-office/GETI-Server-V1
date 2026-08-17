package team.inreok.getiserver.domain.recommendation.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

/**
 * 추천 Card에 표시할 Job 정보다(Recommendation R3, Issue #152). `job`/`company` Entity를 직접
 * 참조하지 않고 `job.query.JobRecommendationCandidateQueryPort`/`company.query.CompanyQuery`가
 * 돌려준 값만 옮겨 담는다 -- `job.dto`/`search.dto`의 기존 Job 응답 Class를 그대로 재사용하지
 * 않는 이유는 Domain 간 DTO 직접 의존을 만들지 않기 위해서다(다른 Module의 `dto` Package는
 * Named Interface로 공개되지 않은 내부 구현이라 애초에 참조할 수 없다).
 */
@Schema(description = "추천 Card에 표시할 공고 정보")
data class RecommendationJobResponse(
    @param:Schema(description = "공고 ID", example = "1")
    val jobId: Long,
    @param:Schema(description = "공고 제목", example = "백엔드 개발 인턴")
    val title: String,
    @param:Schema(description = "기업명. 기업이 삭제됐거나 조회할 수 없으면 null.", example = "인력개발원", nullable = true)
    val companyName: String?,
    @param:Schema(
        description = "기업 로고 URL. 서버가 서명한 짧은 유효기간의 URL이며, 로고가 없으면 null.",
        nullable = true,
    )
    val companyLogoUrl: String?,
    @param:Schema(description = "모집 마감 시각. 상시 채용 등 마감일이 없으면 null.", nullable = true)
    val recruitmentEndedAt: LocalDateTime?,
)
