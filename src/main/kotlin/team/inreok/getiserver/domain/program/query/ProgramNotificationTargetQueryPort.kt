package team.inreok.getiserver.domain.program.query

import org.springframework.modulith.NamedInterface

/**
 * `notification` Module이 알림의 이동 대상(프로그램)이 아직 접근 가능한지 계산할 때 쓰는 공개
 * 계약이다(원본 요구사항 문서 17절). `notification`은 이 Interface를 통해서만 Program을 읽고,
 * `Program` Entity나 `ProgramRepository`를 직접 참조하지 않는다.
 *
 * `job`의 [team.inreok.getiserver.domain.job.query.JobNotificationTargetQueryPort]와 같은 목적의
 * 계약을 Program 쪽에서 공개하는 대응 관계이며, 그 KDoc이 설명한 의존 방향(소유 Domain이 공개,
 * `notification`이 참조)과 판정 책임 분리(원시 상태만 반환) 근거를 그대로 따른다.
 */
@NamedInterface
interface ProgramNotificationTargetQueryPort {
    /**
     * 존재하지 않는 id는 결과 Map에서 빠진다. 삭제된 프로그램(deletedAt != null)도 포함해서
     * 돌려준다. 목록 API가 한 번에 최대 100건을 반환하므로 배치 조회로 둔다(N+1 방지).
     */
    fun findAllByIds(programIds: Set<Long>): Map<Long, ProgramNotificationTargetSnapshot>
}

@NamedInterface
data class ProgramNotificationTargetSnapshot(
    val programId: Long,
    /** `ProgramStatus.name`. DRAFT와 DELETED를 포함한 실제 상태 그대로다. */
    val status: String,
    val deleted: Boolean,
)
