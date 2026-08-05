package team.inreok.getiserver.domain.job.query

import org.springframework.modulith.NamedInterface
import java.time.LocalDateTime

/**
 * `application` Module이 공고-양식 연결 권한 검증(요구사항 6절)과 학생 지원 가능 여부 판단
 * (7절)에 필요한 공고 정보를 읽는 유일한 공개 계약이다(Application Epic #75, Issue #78).
 * `application`은 이 Interface를 통해서만 Job을 읽고, `Job` Entity나 `JobRepository`를
 * 직접 참조하지 않는다.
 *
 * [JobIndexQueryPort]와 달리 공개 대상 여부([team.inreok.getiserver.domain.job.service.PUBLIC_VISIBLE_STATUSES])로
 * 걸러내지 않는다 — DRAFT를 포함한 실제 상태를 그대로 돌려주고, "지원 가능한 상태인지" 판단은
 * `application`이 직접 수행한다(요구사항 7절 "공고가 PUBLISHED 상태인가"는 Application의 판단 항목).
 */
@NamedInterface
interface JobApplicationSnapshotQueryPort {
    /** 존재하지 않거나 삭제됐으면 null을 반환한다. */
    fun findById(jobId: Long): JobApplicationJobSnapshot?
}

@NamedInterface
data class JobApplicationJobSnapshot(
    val jobId: Long,
    val title: String,
    val companyId: Long,
    /** `PostingType.name` */
    val postingType: String,
    /** `ApplicationMethod.name` */
    val applicationMethod: String,
    /** `JobStatus.name`. DRAFT를 포함한 실제 상태 그대로다(DELETED는 deletedAt과 함께 걸러져 null로 반환됨). */
    val status: String,
    val targetGrade: Int?,
    val recruitmentStartedAt: LocalDateTime?,
    val recruitmentEndedAt: LocalDateTime?,
    val createdByMemberId: Long?,
    val managerMemberId: Long?,
)
