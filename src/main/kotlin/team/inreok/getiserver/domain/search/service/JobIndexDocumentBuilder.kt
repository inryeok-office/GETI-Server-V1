package team.inreok.getiserver.domain.search.service

import org.springframework.stereotype.Component
import team.inreok.getiserver.domain.ai.query.AiAnalysisSearchQueryPort
import team.inreok.getiserver.domain.ai.query.AiAnalysisSearchSnapshot
import team.inreok.getiserver.domain.company.query.CompanyQuery
import team.inreok.getiserver.domain.job.query.JobIndexQueryPort
import team.inreok.getiserver.domain.job.query.JobIndexSnapshot
import team.inreok.getiserver.domain.search.document.JobSearchDocument

/**
 * [JobIndexQueryPort]가 돌려주는 Job 원본 값에 Company 정보와 AI 분석 결과를 더해 Elasticsearch
 * Document를 만든다(Issue #69, Issue #144). 증분 동기화([buildFor])와 전체 Reindex
 * (`SearchReindexServiceImpl`)가 모두 이 Class를 거치는 단일 조립 경로다.
 *
 * AI 분석 결과([AiAnalysisSearchSnapshot])는 호출 측이 조회해서 넘겨준다 — 전체 Reindex는 Batch
 * 조회(`AiAnalysisSearchQueryPort.findCompletedByJobIds`) 결과를 여러 건에 나눠 전달해야 하므로,
 * 이 Class가 매번 단건 조회를 하면 Job 건수만큼 반복 호출(N+1)이 생긴다(Issue #144 요구사항).
 */
@Component
class JobIndexDocumentBuilder(
    private val jobIndexQueryPort: JobIndexQueryPort,
    private val companyQuery: CompanyQuery,
    private val aiAnalysisSearchQueryPort: AiAnalysisSearchQueryPort,
) {
    /** 공개 검색 대상이 아니면(존재하지 않거나 DRAFT/삭제) null을 반환한다 — 호출 측이 색인 제거로 처리한다. */
    fun buildFor(jobId: Long): JobSearchDocument? {
        val snapshot = jobIndexQueryPort.findById(jobId) ?: return null
        val aiSnapshot = aiAnalysisSearchQueryPort.findCompletedByJobIds(listOf(jobId))[jobId]
        return build(snapshot, aiSnapshot)
    }

    /**
     * [aiSnapshot]은 호출 측이 미리 조회한 AI 분석 결과다 -- 없으면(아직 분석되지 않았거나
     * COMPLETED가 아니면) null이고, 관련 Field는 빈 값/null로 채운다.
     */
    fun build(
        snapshot: JobIndexSnapshot,
        aiSnapshot: AiAnalysisSearchSnapshot?,
    ): JobSearchDocument {
        val company = companyQuery.findActiveSummary(snapshot.companyId)
        val companyType = companyQuery.findActiveType(snapshot.companyId)
        val companyLogoFileId = companyQuery.findActiveLogoFileId(snapshot.companyId)
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
            companyLogoFileId = companyLogoFileId,
            sourceName = snapshot.sourceName,
            location = snapshot.location,
            employmentType = snapshot.employmentType,
            targetGrade = snapshot.targetGrade,
            capacity = snapshot.capacity,
            firstComeServed = snapshot.firstComeServed,
            viewCount = snapshot.viewCount,
            publishedAt = snapshot.publishedAt,
            startDate = snapshot.startDate,
            endDate = snapshot.endDate,
            requiredTechStackIds = aiSnapshot?.requiredTechStackIds ?: emptyList(),
            preferredTechStackIds = aiSnapshot?.preferredTechStackIds ?: emptyList(),
            highSchoolGraduateFit = aiSnapshot?.highSchoolGraduateFit,
            entryLevelFit = aiSnapshot?.entryLevelFit,
            difficulty = aiSnapshot?.difficulty,
        )
    }
}
