package team.inreok.getiserver.domain.job.upsert.impl

import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.company.query.CompanyQuery
import team.inreok.getiserver.domain.job.entity.Job
import team.inreok.getiserver.domain.job.entity.type.ApplicationMethod
import team.inreok.getiserver.domain.job.entity.type.JobStatus
import team.inreok.getiserver.domain.job.entity.type.PostingType
import team.inreok.getiserver.domain.job.event.JobChangedEvent
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
    private val eventPublisher: ApplicationEventPublisher,
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
            location = normalizeShortText(command.location)
            employmentType = normalizeShortText(command.employmentType)
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
        // Transaction Commit 이후에만 실제로 전달된다(@TransactionalEventListener). 색인 동기화가
        // 실패해도 이 Upsert 자체를 Rollback하지 않는다(Issue #69, PostgreSQL이 원본 유지).
        eventPublisher.publishEvent(JobChangedEvent(requireNotNull(saved.id)))
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
            existing.recruitmentEndedAt != command.endDate ||
            existing.location != normalizeShortText(command.location) ||
            existing.employmentType != normalizeShortText(command.employmentType)

    /**
     * 외부 Provider가 준 근무지역·고용형태를 Column 제약에 맞게 정리한다(Issue #169).
     *
     * 잘라서라도 저장하는 이유는, 값 하나가 길다는 이유로 예외를 던지면 그 공고 자체가 저장되지
     * 않고 CollectionRunError로 버려지기 때문이다. 외부 값은 우리가 고칠 수 없으므로 표시용
     * 문자열 하나 때문에 공고 전체를 잃지 않는다(`DiscordPayloadFactory`가 Bot Schema 길이
     * 제한을 다루는 방식과 같다). 사용자가 직접 입력하는 등록·수정 API는 이 경로를 타지 않고
     * Bean Validation이 먼저 400으로 막는다.
     *
     * [hasContentChanged]도 같은 정규화를 거친 값끼리 비교한다. 저장 값과 비교 값이 다르면 같은
     * 입력인데도 매번 "변경됨"으로 판정해 불필요한 Update와 색인 이벤트가 반복된다.
     */
    private fun normalizeShortText(value: String?): String? =
        value?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX_SHORT_TEXT_LENGTH)

    companion object {
        /** `jobs.location`/`jobs.employment_type`의 Column 길이다(V26). */
        private const val MAX_SHORT_TEXT_LENGTH = 255
    }
}
