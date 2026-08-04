package team.inreok.getiserver.domain.job.upsert

import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.modulith.NamedInterface
import java.time.LocalDateTime

/**
 * Collector 도메인이 외부에서 수집·정규화한 공고를 Job에 반영할 때 사용하는 유일한 공개 계약이다.
 * Collector는 이 Interface를 통해서만 Job에 접근하고, `JobRepository`나 `Job` Entity를 직접
 * 참조하지 않는다(docs/architecture/modularity.md, Issue #62). 등록·수정은 Job Module의
 * 내부 검증 규칙(`domain.job.service.validateCommon`/`validateForPublish`)을 그대로 재사용해
 * Collector가 별도로 규칙을 복제하지 않는다.
 */
@NamedInterface
interface CollectedJobUpsertUseCase {
    /**
     * `(sourceName, externalJobId)` 조합으로 기존 공고를 찾아 있으면 갱신하고 없으면 새로 만든다
     * (Job의 기존 `uk_jobs_source_external_id` Partial Unique Index를 그대로 사용하는 멱등 키).
     * 게시 필수값을 만족하지 못하면 [CollectedJobUpsertCommand.publish]가 true여도 임시저장
     * (DRAFT)으로 남는다.
     */
    fun upsert(command: CollectedJobUpsertCommand): CollectedJobUpsertResult
}

@NamedInterface
@Schema(description = "Collector가 수집한 공고를 Job에 반영하기 위한 명령. companyId는 호출 측이 이미 해석한 값이어야 한다.")
data class CollectedJobUpsertCommand(
    @param:Schema(description = "공고가 속한 기업 ID. Company 공개 계약으로 이미 확인된 값이어야 한다.")
    val companyId: Long,
    @param:Schema(description = "외부 수집 출처. Job의 source_name Column에 그대로 저장된다.", example = "MMA")
    val sourceName: String,
    @param:Schema(description = "출처 기준 외부 공고 ID", example = "2026-000123")
    val externalJobId: String,
    @param:Schema(description = "공고 제목")
    val title: String,
    @param:Schema(description = "공고 본문(nullable)", nullable = true)
    val content: String?,
    @param:Schema(description = "외부 지원 URL(nullable)", nullable = true)
    val externalUrl: String?,
    @param:Schema(description = "모집 시작 시각(nullable)", nullable = true)
    val startDate: LocalDateTime?,
    @param:Schema(description = "모집 종료 시각(nullable)", nullable = true)
    val endDate: LocalDateTime?,
    @param:Schema(description = "true면 게시 필수값을 만족할 때 PUBLISHED로 반영을 시도한다. 실패하면 DRAFT로 저장된다.")
    val publish: Boolean,
)

@NamedInterface
enum class JobImportOutcome {
    CREATED,
    UPDATED,
    UNCHANGED,

    /** 이 Use Case 구현은 실패를 예외로 알리므로 실제로 반환하지는 않지만, 호출 측 계약 문서화를 위해 남긴다. */
    FAILED,
}

@NamedInterface
@Schema(description = "Job 반영 결과")
data class CollectedJobUpsertResult(
    @param:Schema(description = "반영된 공고 ID")
    val jobId: Long,
    @param:Schema(description = "생성/갱신/변경 없음 여부")
    val outcome: JobImportOutcome,
    @param:Schema(description = "실제로 PUBLISHED 상태로 반영되었는지 여부")
    val published: Boolean,
)
