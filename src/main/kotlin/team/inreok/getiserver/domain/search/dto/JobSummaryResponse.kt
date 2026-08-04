package team.inreok.getiserver.domain.search.dto

import io.swagger.v3.oas.annotations.media.Schema
import team.inreok.getiserver.domain.company.query.CompanySummary
import team.inreok.getiserver.domain.job.entity.type.ApplicationMethod
import team.inreok.getiserver.domain.job.entity.type.JobStatus
import team.inreok.getiserver.domain.job.entity.type.PostingType
import team.inreok.getiserver.domain.search.document.JobSearchDocument
import java.time.LocalDateTime

@Schema(description = "공고 목록 항목")
data class JobSummaryResponse(
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
    @param:Schema(description = "모집 시작 시각", example = "2026-08-01T00:00:00", nullable = true)
    val startDate: LocalDateTime?,
    @param:Schema(description = "모집 종료 시각. null이면 마감 없는 공고다.", example = "2026-08-31T23:59:59", nullable = true)
    val endDate: LocalDateTime?,
    @param:Schema(description = "지원 대상 학년(1~3). null이면 학년 제한이 없다.", example = "3", nullable = true)
    val targetGrade: Int?,
    @param:Schema(description = "모집 인원", example = "2", nullable = true)
    val capacity: Int?,
    @param:Schema(description = "선착순 모집 여부", example = "false")
    val firstComeServed: Boolean,
    @param:Schema(description = "조회수", example = "128")
    val viewCount: Long,
    @param:Schema(description = "게시 시각", example = "2026-07-25T09:00:00", nullable = true)
    val publishedAt: LocalDateTime?,
) {
    companion object {
        /**
         * 검색 결과(Elasticsearch Document)를 그대로 응답으로 옮긴다. PostgreSQL을 다시 조회하지
         * 않는다(Issue #69 — Elasticsearch가 검색 전용 Read Model이라는 원칙, 완료 보고 참고).
         */
        fun from(document: JobSearchDocument): JobSummaryResponse =
            JobSummaryResponse(
                jobId = document.jobId,
                title = document.title,
                postingType = PostingType.valueOf(document.postingType),
                applicationMethod = ApplicationMethod.valueOf(document.applicationMethod),
                status = JobStatus.valueOf(document.status),
                company = document.companyName?.let { CompanySummary(companyId = document.companyId, name = it) },
                startDate = document.startDate,
                endDate = document.endDate,
                targetGrade = document.targetGrade,
                capacity = document.capacity,
                firstComeServed = document.firstComeServed,
                viewCount = document.viewCount,
                publishedAt = document.publishedAt,
            )
    }
}
