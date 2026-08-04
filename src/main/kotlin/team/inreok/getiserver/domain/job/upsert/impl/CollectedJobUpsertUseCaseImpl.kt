package team.inreok.getiserver.domain.job.upsert.impl

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.company.query.CompanyQuery
import team.inreok.getiserver.domain.job.entity.Job
import team.inreok.getiserver.domain.job.entity.type.ApplicationMethod
import team.inreok.getiserver.domain.job.entity.type.JobStatus
import team.inreok.getiserver.domain.job.entity.type.PostingType
import team.inreok.getiserver.domain.job.exception.JobCompanyNotFoundException
import team.inreok.getiserver.domain.job.repository.JobRepository
import team.inreok.getiserver.domain.job.service.validateCommon
import team.inreok.getiserver.domain.job.service.validateForPublish
import team.inreok.getiserver.domain.job.upsert.CollectedJobUpsertCommand
import team.inreok.getiserver.domain.job.upsert.CollectedJobUpsertResult
import team.inreok.getiserver.domain.job.upsert.CollectedJobUpsertUseCase
import team.inreok.getiserver.domain.job.upsert.JobImportOutcome
import java.time.LocalDateTime

@Service
class CollectedJobUpsertUseCaseImpl(
    private val jobRepository: JobRepository,
    private val companyQuery: CompanyQuery,
) : CollectedJobUpsertUseCase {
    @Transactional
    override fun upsert(command: CollectedJobUpsertCommand): CollectedJobUpsertResult {
        // Collector가 해석한 companyId가 실제로 존재하는 활성 기업인지 다시 확인한다(Job.create와
        // 동일한 방어). 없으면 저장하지 않고 실패시켜 CollectionRunError로 기록되게 한다.
        companyQuery.findActiveSummary(command.companyId) ?: throw JobCompanyNotFoundException(command.companyId)

        val existing = jobRepository.findBySourceNameAndExternalJobId(command.sourceName, command.externalJobId)
        if (existing != null && !hasContentChanged(existing, command)) {
            return CollectedJobUpsertResult(
                jobId = requireNotNull(existing.id),
                outcome = JobImportOutcome.UNCHANGED,
                published = existing.status == JobStatus.PUBLISHED,
            )
        }

        val job =
            existing ?: Job(
                companyId = command.companyId,
                type = PostingType.GENERAL,
                applicationMethod = ApplicationMethod.EXTERNAL,
                title = command.title.trim(),
                status = JobStatus.DRAFT,
            ).apply {
                sourceName = command.sourceName
                externalJobId = command.externalJobId
            }

        job.apply {
            title = command.title.trim()
            bodyMarkdown = command.content
            externalUrl = command.externalUrl
            recruitmentStartedAt = command.startDate
            recruitmentEndedAt = command.endDate
        }

        validateCommon(job)

        // 게시 필수값을 만족하지 못하면 publish 요청이어도 DRAFT로 남긴다(부분 품질 공고를 공개
        // 목록에 노출하지 않기 위함). 이미 PUBLISHED였던 공고가 갱신 후 필수값을 잃는 경우는
        // 이번 범위에서 자동 강등하지 않는다 — 명세에 없는 정책이라 임의로 추가하지 않았다.
        if (command.publish && job.status != JobStatus.PUBLISHED) {
            val canPublish = runCatching { validateForPublish(job) }.isSuccess
            if (canPublish) {
                job.status = JobStatus.PUBLISHED
                job.publishedAt = LocalDateTime.now()
            }
        }

        val saved = jobRepository.saveAndFlush(job)
        return CollectedJobUpsertResult(
            jobId = requireNotNull(saved.id),
            outcome = if (existing == null) JobImportOutcome.CREATED else JobImportOutcome.UPDATED,
            published = saved.status == JobStatus.PUBLISHED,
        )
    }

    // 단순 updatedAt 변경만으로 항상 Update하지 않기 위해, 실제로 노출되는 값이 바뀌었을 때만
    // 갱신한다(Issue #62 확정 정책). 게시 상태 자체(PUBLISHED로의 전환 시도)는 이 비교에 포함하지
    // 않는다 — 값이 그대로라면 이전 호출에서 이미 같은 publish 판정을 거쳤다고 보기 때문이다.
    private fun hasContentChanged(
        existing: Job,
        command: CollectedJobUpsertCommand,
    ): Boolean =
        existing.title != command.title.trim() ||
            existing.bodyMarkdown != command.content ||
            existing.externalUrl != command.externalUrl ||
            existing.recruitmentStartedAt != command.startDate ||
            existing.recruitmentEndedAt != command.endDate
}
