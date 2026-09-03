package team.inreok.getiserver.domain.job.dto

import io.swagger.v3.oas.annotations.media.Schema
import team.inreok.getiserver.domain.company.query.CompanySummary
import team.inreok.getiserver.domain.job.access.JobAiAnalysisAccessSnapshot
import team.inreok.getiserver.domain.job.access.JobApplicationEligibilityAccessSnapshot
import team.inreok.getiserver.domain.job.entity.type.ApplicationMethod
import team.inreok.getiserver.domain.job.entity.type.JobStatus
import team.inreok.getiserver.domain.job.entity.type.PostingType
import java.time.LocalDateTime

/** 관리자 공고 상세 응답이다. 공개 상세에는 [manager]를 노출하지 않는다. */
@Schema(description = "관리자 공고 상세 응답")
data class JobAdminDetailResponse(
    val jobId: Long,
    val title: String,
    val postingType: PostingType,
    val applicationMethod: ApplicationMethod,
    val status: JobStatus,
    val company: CompanySummary?,
    val content: String?,
    val externalUrl: String?,
    val startDate: LocalDateTime?,
    val endDate: LocalDateTime?,
    val targetGrade: Int?,
    val capacity: Int?,
    val location: String?,
    val employmentType: String?,
    val sourceName: String?,
    val firstComeServed: Boolean,
    val viewCount: Long,
    val publishedAt: LocalDateTime?,
    val closedAt: LocalDateTime?,
    val createdAt: LocalDateTime?,
    val updatedAt: LocalDateTime?,
    val aiAnalysis: JobAiAnalysisAccessSnapshot?,
    val application: JobApplicationEligibilityAccessSnapshot,
    val bookmarked: Boolean,
    val files: List<JobFileResponse>,
    @param:Schema(description = "공고 담당자. 명시 담당자가 없으면 등록자이며, 둘 다 없으면 null입니다.", nullable = true)
    val manager: JobManagerResponse?,
) {
    companion object {
        fun from(
            detail: JobDetailResponse,
            manager: JobManagerResponse?,
        ) = JobAdminDetailResponse(
            detail.jobId,
            detail.title,
            detail.postingType,
            detail.applicationMethod,
            detail.status,
            detail.company,
            detail.content,
            detail.externalUrl,
            detail.startDate,
            detail.endDate,
            detail.targetGrade,
            detail.capacity,
            detail.location,
            detail.employmentType,
            detail.sourceName,
            detail.firstComeServed,
            detail.viewCount,
            detail.publishedAt,
            detail.closedAt,
            detail.createdAt,
            detail.updatedAt,
            detail.aiAnalysis,
            detail.application,
            detail.bookmarked,
            detail.files,
            manager,
        )
    }
}
