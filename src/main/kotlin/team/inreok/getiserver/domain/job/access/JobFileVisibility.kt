package team.inreok.getiserver.domain.job.access

import team.inreok.getiserver.domain.job.entity.Job
import team.inreok.getiserver.domain.job.service.PUBLIC_VISIBLE_STATUSES

/**
 * "이 회원이 이 공고의 첨부파일(`FileOwnerType.JOB`)을 볼 수 있는가"를 판정하는 순수 함수다.
 * Repository/Port 호출이 없어 두 호출부가 항상 같은 규칙을 적용하도록 강제한다
 * (`program.access.canViewProgramFiles`와 동일한 방식, Issue #126).
 *
 * 두 곳에서 이 함수를 그대로 재사용한다.
 * 1. [JobFileAccessChecker.canDownload] -- 실제 다운로드(`FileController`) 권한 판정.
 * 2. `JobServiceImpl` -- 상세 응답의 `files` 목록 노출 여부.
 *
 * `managerMemberId`를 기준에 포함하지만, 공고에 담당 교사를 지정하는 API가 아직 없어(`Job` Entity
 * 주석, `JobRepository.findIdsByCompanyOrManagerFilter` 주석 참고) 실질적으로는 등록자와 동일하게
 * 항상 `null`이다. 나중에 담당자 지정 기능이 생겨도 이 함수를 다시 손댈 필요가 없도록 필드를 먼저
 * 반영해 둔다.
 *
 * `isDeveloper`를 `() -> Boolean` 지연 평가로 받는 이유는 PUBLISHED/CLOSED 조기 반환 경로(공개
 * 상세 조회 -- 트래픽 대부분)에서 `MemberRoleQueryPort.findRoles` 같은 비용이 드는 Role 조회를
 * 아예 실행하지 않기 위해서다(`canViewProgramFiles`와 동일한 이유).
 */
fun canViewJobFiles(
    job: Job,
    requesterId: Long,
    isDeveloper: () -> Boolean,
): Boolean {
    val isPublic = job.status in PUBLIC_VISIBLE_STATUSES
    val isOwnerOrManager = requesterId == job.createdByMemberId || requesterId == job.managerMemberId
    // `||`는 단락 평가(Short-circuit)라서 isPublic 또는 isOwnerOrManager가 true면 isDeveloper()는
    // 실행되지 않는다.
    return isPublic || isOwnerOrManager || isDeveloper()
}
