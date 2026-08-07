package team.inreok.getiserver.domain.job.query

import org.springframework.modulith.NamedInterface

/**
 * `notification` Module이 알림의 이동 대상(공고)이 아직 접근 가능한지 계산할 때 쓰는 공개
 * 계약이다(원본 요구사항 문서 17절). `notification`은 이 Interface를 통해서만 Job을 읽고,
 * `Job` Entity나 `JobRepository`를 직접 참조하지 않는다.
 *
 * 이 계약이 `notification`이 아니라 `job`에 있는 이유: Interface를 `notification`이 소유하고
 * `job`이 구현하면 `job -> notification` 의존이 생기는데, 이후 `notification`이
 * `job.event.JobChangedEvent` 같은 Domain Event를 구독하면 반대 방향 의존이 함께 생겨
 * `ModularityTest`(`modules.verify()`)가
 * 순환 의존으로 실패한다. 저장소의 다른 Port(`job.upsert`, `member.query`, `application.query`,
 * `company.query`)와 같은 "소유 Domain이 공개하고 소비자가 참조하는" 방향을 유지한다.
 *
 * [JobApplicationSnapshotQueryPort]와 같은 이유로 공개 대상 여부로 걸러내지 않는다 — DRAFT와
 * 삭제 여부를 그대로 돌려주고, "접근 가능한가"와 그 이유(DELETED/NOT_VISIBLE)를 고르는 판단은
 * `notification`이 직접 수행한다. 판정 결과 Enum을 여기서 돌려주면 `job`이 `notification`의
 * 타입을 알아야 해서 다시 순환이 생긴다.
 */
@NamedInterface
interface JobNotificationTargetQueryPort {
    /**
     * 존재하지 않는 id는 결과 Map에서 빠진다. 삭제된 공고(deletedAt != null)도 포함해서
     * 돌려준다 — 호출 측이 "삭제됨"과 "원래 없음"을 굳이 구분하지 않지만, 삭제를 이유로 걸러내면
     * 상태 정보 자체를 잃기 때문이다.
     *
     * 목록 API가 한 번에 최대 100건을 반환하므로 단건이 아닌 배치 조회로 둔다(N+1 방지).
     */
    fun findAllByIds(jobIds: Set<Long>): Map<Long, JobNotificationTargetSnapshot>
}

@NamedInterface
data class JobNotificationTargetSnapshot(
    val jobId: Long,
    /** `JobStatus.name`. DRAFT와 DELETED를 포함한 실제 상태 그대로다. */
    val status: String,
    val deleted: Boolean,
)
