package team.inreok.getiserver.domain.application.dto

import io.swagger.v3.oas.annotations.media.Schema
import team.inreok.getiserver.domain.job.entity.type.JobStatus

@Schema(description = "담당 공고별 지원 현황 요약 결과")
data class JobApplicationJobSummaryResponse(
    @param:Schema(description = "담당 공고별 요약 목록")
    val content: List<JobApplicationJobSummaryItemResponse>,
    @param:Schema(description = "현재 Page 번호(0부터 시작)", example = "0")
    val page: Int,
    @param:Schema(description = "Page 크기", example = "20")
    val size: Int,
    @param:Schema(description = "전체 담당 공고 수", example = "3")
    val totalElements: Long,
    @param:Schema(description = "전체 Page 수", example = "1")
    val totalPages: Int,
    @param:Schema(description = "첫 Page 여부", example = "true")
    val first: Boolean,
    @param:Schema(description = "마지막 Page 여부", example = "true")
    val last: Boolean,
)

@Schema(description = "담당 공고 한 건의 지원 현황 요약")
data class JobApplicationJobSummaryItemResponse(
    @param:Schema(description = "공고 ID", example = "10")
    val jobId: Long,
    @param:Schema(description = "공고명", example = "Backend 개발자 인턴")
    val jobTitle: String,
    @param:Schema(description = "공고 상태", example = "PUBLISHED")
    val jobStatus: JobStatus,
    @param:Schema(description = "DRAFT 지원서를 제외한 전체 지원자 수", example = "12")
    val applicantCount: Long,
    @param:Schema(description = "교직원 처리가 필요한 SUBMITTED·EDIT_REQUESTED 지원서 수", example = "4")
    val pendingCount: Long,
)
