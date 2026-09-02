package team.inreok.getiserver.domain.application.query

import org.springframework.modulith.NamedInterface

/**
 * `notification` Module이 알림의 이동 대상(지원서)이 아직 접근 가능한지 계산할 때 쓰는 공개
 * 계약이다(Issue #187). `notification`은 이 Interface를 통해서만 JobApplication을 읽고,
 * `JobApplication` Entity나 `JobApplicationRepository`를 직접 참조하지 않는다.
 *
 * 방향과 반환 형태의 이유는
 * [team.inreok.getiserver.domain.inquiry.query.InquiryNotificationTargetQueryPort]와 같다.
 */
@NamedInterface
interface JobApplicationNotificationTargetQueryPort {
    /**
     * 존재하지 않는 id는 결과 Map에서 빠진다(다른 Notification Target Port와 같은 관례).
     *
     * 목록 API가 한 번에 최대 100건을 반환하므로 단건이 아닌 배치 조회로 둔다(N+1 방지).
     */
    fun findAllByIds(applicationIds: Set<Long>): Map<Long, JobApplicationNotificationTargetSnapshot>
}

/**
 * `job_applications`는 Soft Delete Column이 없어 "삭제됨"은 Row가 없는 것과 같다. 상태
 * (`JobApplicationStatus`)도 담지 않는다 -- 지원자 본인은 DRAFT를 포함한 자기 지원서를 언제나
 * 열람할 수 있고(교사 전용 조회만 DRAFT를 가린다), 이 대상의 알림은 검토 결과에서만 생성되기
 * 때문이다.
 */
@NamedInterface
data class JobApplicationNotificationTargetSnapshot(
    val applicationId: Long,
    val applicantMemberId: Long,
)
