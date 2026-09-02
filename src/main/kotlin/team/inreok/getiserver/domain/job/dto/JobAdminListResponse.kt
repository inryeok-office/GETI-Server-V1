package team.inreok.getiserver.domain.job.dto

import io.swagger.v3.oas.annotations.media.Schema
import team.inreok.getiserver.domain.company.query.CompanySummary
import team.inreok.getiserver.domain.job.entity.Job
import team.inreok.getiserver.domain.job.entity.type.ApplicationMethod
import team.inreok.getiserver.domain.job.entity.type.JobStatus
import team.inreok.getiserver.domain.job.entity.type.PostingType
import java.time.LocalDateTime

@Schema(description = "관리자 공고 목록 결과. page는 0부터 시작한다.")
data class JobAdminListResponse(
    @param:Schema(description = "관리자 공고 목록")
    val content: List<JobAdminListItemResponse>,
    @param:Schema(description = "현재 Page 번호(0부터 시작)", example = "0")
    val page: Int,
    @param:Schema(description = "Page당 개수", example = "20")
    val size: Int,
    @param:Schema(description = "전체 결과 개수", example = "42")
    val totalElements: Long,
    @param:Schema(description = "전체 Page 수", example = "3")
    val totalPages: Int,
    @param:Schema(description = "첫 Page 여부", example = "true")
    val first: Boolean,
    @param:Schema(description = "마지막 Page 여부", example = "false")
    val last: Boolean,
)

@Schema(description = "관리자 공고 목록 항목")
data class JobAdminListItemResponse(
    @param:Schema(description = "공고 ID", example = "1")
    val jobId: Long,
    @param:Schema(description = "공고 제목", example = "2026 상반기 백엔드 채용")
    val title: String,
    @param:Schema(description = "기업 요약. 기업이 삭제되면 null이 될 수 있다.", nullable = true)
    val company: CompanySummary?,
    @param:Schema(description = "공고 유형", example = "MOU")
    val postingType: PostingType,
    @param:Schema(description = "지원 방식", example = "EXTERNAL")
    val applicationMethod: ApplicationMethod,
    @param:Schema(description = "공고 상태", example = "DRAFT")
    val status: JobStatus,
    @param:Schema(description = "모집 시작 시각", nullable = true)
    val startDate: LocalDateTime?,
    @param:Schema(description = "모집 종료 시각", nullable = true)
    val endDate: LocalDateTime?,
    @param:Schema(description = "생성 시각", nullable = true)
    val createdAt: LocalDateTime?,
    @param:Schema(description = "최종 수정 시각", nullable = true)
    val updatedAt: LocalDateTime?,
) {
    companion object {
        fun from(
            job: Job,
            company: CompanySummary?,
        ): JobAdminListItemResponse =
            JobAdminListItemResponse(
                jobId = requireNotNull(job.id) { "저장된 Job은 id를 가져야 합니다." },
                title = job.title,
                company = company,
                postingType = job.type,
                applicationMethod = job.applicationMethod,
                status = job.status,
                startDate = job.recruitmentStartedAt,
                endDate = job.recruitmentEndedAt,
                createdAt = job.createdAt,
                updatedAt = job.updatedAt,
            )
    }
}
