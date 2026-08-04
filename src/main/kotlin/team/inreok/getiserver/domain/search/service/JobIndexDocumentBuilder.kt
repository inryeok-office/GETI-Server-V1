package team.inreok.getiserver.domain.search.service

import org.springframework.stereotype.Component
import team.inreok.getiserver.domain.company.query.CompanyQuery
import team.inreok.getiserver.domain.job.query.JobIndexQueryPort
import team.inreok.getiserver.domain.job.query.JobIndexSnapshot
import team.inreok.getiserver.domain.search.document.JobSearchDocument

/**
 * [JobIndexQueryPort]가 돌려주는 Job 원본 값에 Company 정보를 더해 Elasticsearch Document를
 * 만든다(Issue #69). AI 분석 필드는 포함하지 않는다 — AI Use Case가 아직 구현되지 않아 실제로
 * 채울 값이 없고, 동작하지 않는 연동을 미리 만들지 않는다(Issue #69 제외 범위).
 */
@Component
class JobIndexDocumentBuilder(
    private val jobIndexQueryPort: JobIndexQueryPort,
    private val companyQuery: CompanyQuery,
) {
    /** 공개 검색 대상이 아니면(존재하지 않거나 DRAFT/삭제) null을 반환한다 — 호출 측이 색인 제거로 처리한다. */
    fun buildFor(jobId: Long): JobSearchDocument? = jobIndexQueryPort.findById(jobId)?.let(::build)

    fun build(snapshot: JobIndexSnapshot): JobSearchDocument {
        val company = companyQuery.findActiveSummary(snapshot.companyId)
        val companyType = companyQuery.findActiveType(snapshot.companyId)
        return JobSearchDocument(
            id = snapshot.jobId.toString(),
            jobId = snapshot.jobId,
            title = snapshot.title,
            content = snapshot.content,
            postingType = snapshot.postingType,
            applicationMethod = snapshot.applicationMethod,
            status = snapshot.status,
            companyId = snapshot.companyId,
            // 공고 등록 후 기업이 삭제될 수 있다. company가 null이면 companyName도 null로 두고,
            // JobSummaryResponse.from이 이를 company: CompanySummary? = null로 그대로 옮긴다
            // (기업이 삭제되면 응답의 company 자체가 null이라는 기존 계약을 유지, PR #70 Review 반영).
            companyName = company?.name,
            companyType = companyType?.name,
            sourceName = snapshot.sourceName,
            targetGrade = snapshot.targetGrade,
            capacity = snapshot.capacity,
            firstComeServed = snapshot.firstComeServed,
            viewCount = snapshot.viewCount,
            publishedAt = snapshot.publishedAt,
            startDate = snapshot.startDate,
            endDate = snapshot.endDate,
        )
    }
}
