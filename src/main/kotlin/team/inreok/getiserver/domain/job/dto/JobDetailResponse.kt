package team.inreok.getiserver.domain.job.dto

import io.swagger.v3.oas.annotations.media.Schema
import team.inreok.getiserver.domain.company.query.CompanySummary
import team.inreok.getiserver.domain.job.access.JobAiAnalysisAccessSnapshot
import team.inreok.getiserver.domain.job.access.JobApplicationEligibilityAccessSnapshot
import team.inreok.getiserver.domain.job.entity.Job
import team.inreok.getiserver.domain.job.entity.type.ApplicationMethod
import team.inreok.getiserver.domain.job.entity.type.JobStatus
import team.inreok.getiserver.domain.job.entity.type.PostingType
import java.time.LocalDateTime

/**
 * 공고 상세 응답이다. 공개 상세와 관리자 상세가 같은 구조를 사용하고, 관리자만 볼 수 있는
 * `DRAFT`/`DELETED` 여부는 [status]로 구분한다. `deletedAt`은 노출하지 않는다.
 *
 * `formId`, `aiRequestAccepted`, `bookmarkCount`는 각각 Application/Recommendation Domain이
 * 준비된 뒤 추가한다. 가짜 값이나 빈 목록을 지금 내보내면 Client가 없는 기능을 있는 것으로
 * 오해하므로 Field 자체를 넣지 않았다.
 *
 * [files]는 Issue #126에서 추가했다. DRAFT 상태에서는 등록자·담당 교사·개발자에게만 실제 목록을
 * 반환하고, 그 외 요청자에게는 빈 목록을 반환한다(`job.access.canViewJobFiles`,
 * `ProgramDetailResponse.files`와 동일한 규칙 — 실제 다운로드 가능 여부보다 더 많은 첨부파일
 * Metadata를 흘리지 않기 위해서다).
 *
 * [aiAnalysis]는 AI Analysis Phase 1(Issue #132)에서 추가했다. 아직 분석이 시작되지 않은(예:
 * PUBLISHED 이후 비동기 Trigger가 아직 처리되지 않은) 공고는 null이다.
 *
 * [application]은 Application Phase 8(Issue #136)에서 추가했다. 요청자(학생 기준) 지원 가능
 * 여부·지원 현황을 서버가 계산해 그대로 노출한다 -- Frontend가 별도로
 * `GET /api/v1/jobs/{jobId}/application-eligibility`를 다시 호출하지 않아도 된다.
 *
 * [bookmarked]는 Issue #171에서 추가했다. 요청자(학생 기준) 북마크 여부를 서버가 계산해 그대로
 * 노출한다(`job.access.JobBookmarkAccessor`, `recommendation`이 구현). PR #178의 북마크
 * 등록·해제 API(Write Path)를 이어받는 Read Integration이다.
 */
@Schema(description = "공고 상세 정보")
data class JobDetailResponse(
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
    @param:Schema(description = "Markdown 본문", example = "## 모집 부문\n- 백엔드 개발자", nullable = true)
    val content: String?,
    @param:Schema(description = "외부 지원 URL", example = "https://example.com/apply", nullable = true)
    val externalUrl: String?,
    @param:Schema(description = "모집 시작 시각", example = "2026-08-01T00:00:00", nullable = true)
    val startDate: LocalDateTime?,
    @param:Schema(description = "모집 종료 시각. null이면 마감 없는 공고다.", example = "2026-08-31T23:59:59", nullable = true)
    val endDate: LocalDateTime?,
    @param:Schema(description = "지원 대상 학년(1~3). null이면 학년 제한이 없다.", example = "3", nullable = true)
    val targetGrade: Int?,
    @param:Schema(description = "모집 인원", example = "2", nullable = true)
    val capacity: Int?,
    @param:Schema(
        description = "근무지역. 정해진 값 집합이 없는 표시 전용 문자열이다. 입력되지 않았으면 null.",
        example = "서울특별시 중구",
        nullable = true,
    )
    val location: String?,
    @param:Schema(
        description = "고용형태. 정해진 값 집합이 없는 표시 전용 문자열이다. 입력되지 않았으면 null.",
        example = "인턴",
        nullable = true,
    )
    val employmentType: String?,
    @param:Schema(
        description = "공고 출처의 안정 식별자. 수집 공고는 JobSource sourceCode와 같고 직접 등록 공고는 null일 수 있다.",
        example = "SARAMIN",
        nullable = true,
    )
    val sourceName: String? = null,
    @param:Schema(description = "선착순 모집 여부", example = "false")
    val firstComeServed: Boolean,
    @param:Schema(description = "조회수. 공개 상세 조회 응답에는 이번 조회분이 반영된 값이 담긴다.", example = "129")
    val viewCount: Long,
    @param:Schema(description = "게시 시각", example = "2026-07-25T09:00:00", nullable = true)
    val publishedAt: LocalDateTime?,
    @param:Schema(description = "마감 시각", example = "2026-09-01T00:00:00", nullable = true)
    val closedAt: LocalDateTime?,
    @param:Schema(description = "생성 시각", example = "2026-07-20T10:00:00", nullable = true)
    val createdAt: LocalDateTime?,
    @param:Schema(description = "최종 수정 시각", example = "2026-07-25T09:00:00", nullable = true)
    val updatedAt: LocalDateTime?,
    @param:Schema(
        description = "AI 공고 분석 결과. AI 분석 결과는 참고용이다. 아직 분석이 시작되지 않았으면 null.",
        nullable = true,
    )
    val aiAnalysis: JobAiAnalysisAccessSnapshot?,
    val application: JobApplicationEligibilityAccessSnapshot,
    @param:Schema(description = "요청자 기준 북마크 여부", example = "false")
    val bookmarked: Boolean,
    @param:Schema(description = "첨부파일 목록. 조회 권한이 없으면(비공개 상태 + 등록자·담당 교사·개발자가 아님) 빈 목록이다.")
    val files: List<JobFileResponse>,
) {
    companion object {
        /**
         * [viewCount]를 따로 받는 이유는 공개 상세 조회가 `UPDATE ... view_count + 1`로 조회수를
         * 올리기 때문이다. 그 UPDATE는 영속성 Context를 거치지 않아 [job]의 값이 낡은 채로 남으므로,
         * 호출 측이 증가 후 값을 명시적으로 넘긴다.
         */
        fun from(
            job: Job,
            company: CompanySummary?,
            viewCount: Long = job.viewCount,
            aiAnalysis: JobAiAnalysisAccessSnapshot? = null,
            application: JobApplicationEligibilityAccessSnapshot,
            bookmarked: Boolean,
            files: List<JobFileResponse>,
        ): JobDetailResponse =
            JobDetailResponse(
                jobId = requireNotNull(job.id) { "저장된 Job은 id를 가져야 합니다." },
                title = job.title,
                postingType = job.type,
                applicationMethod = job.applicationMethod,
                status = job.status,
                company = company,
                content = job.bodyMarkdown,
                externalUrl = job.externalUrl,
                startDate = job.recruitmentStartedAt,
                endDate = job.recruitmentEndedAt,
                targetGrade = job.targetGrade,
                capacity = job.capacity,
                location = job.location,
                employmentType = job.employmentType,
                sourceName = job.sourceName,
                firstComeServed = job.firstComeServed,
                viewCount = viewCount,
                publishedAt = job.publishedAt,
                closedAt = job.closedAt,
                createdAt = job.createdAt,
                updatedAt = job.updatedAt,
                aiAnalysis = aiAnalysis,
                application = application,
                bookmarked = bookmarked,
                files = files,
            )
    }
}
