package team.inreok.getiserver.domain.search.dto

import io.swagger.v3.oas.annotations.media.Schema
import team.inreok.getiserver.domain.company.query.CompanySummary
import team.inreok.getiserver.domain.job.access.JobApplicationEligibilityAccessSnapshot
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
    val application: JobApplicationEligibilityAccessSnapshot,
) {
    companion object {
        /**
         * 검색 결과(Elasticsearch Document)를 그대로 응답으로 옮긴다. PostgreSQL을 다시 조회하지
         * 않는다(Issue #69 — Elasticsearch가 검색 전용 Read Model이라는 원칙, 완료 보고 참고).
         *
         * [logoUrls]는 `companyLogoFileId -> Presigned URL` Map이다(Issue #92). [application]은
         * 요청자 기준 학생 지원 가능 여부·지원 현황이다(Issue #136). 둘 다 목록 항목마다 단건
         * 조회하면 N+1이 되므로, 호출 측(`JobSearchServiceImpl`)이 검색 결과 전체를 모아 한 번에
         * 조회한 배치 결과를 그대로 전달한다. Elasticsearch에는 만료되는 URL이나 요청자별로 달라지는
         * 값을 저장하지 않으므로(`JobSearchDocument.companyLogoFileId` 참고) 둘 다 이 변환
         * 시점에만 존재한다.
         */
        fun from(
            document: JobSearchDocument,
            logoUrls: Map<Long, String> = emptyMap(),
            application: JobApplicationEligibilityAccessSnapshot,
        ): JobSummaryResponse =
            JobSummaryResponse(
                jobId = document.jobId,
                title = document.title,
                postingType = PostingType.valueOf(document.postingType),
                applicationMethod = ApplicationMethod.valueOf(document.applicationMethod),
                status = JobStatus.valueOf(document.status),
                company =
                    document.companyName?.let {
                        CompanySummary(
                            companyId = document.companyId,
                            name = it,
                            logoUrl = document.companyLogoFileId?.let { fileId -> logoUrls[fileId] },
                        )
                    },
                startDate = document.startDate,
                endDate = document.endDate,
                targetGrade = document.targetGrade,
                capacity = document.capacity,
                firstComeServed = document.firstComeServed,
                viewCount = document.viewCount,
                publishedAt = document.publishedAt,
                application = application,
            )
    }
}
