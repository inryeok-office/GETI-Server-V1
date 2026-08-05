package team.inreok.getiserver.domain.application.exception

import team.inreok.getiserver.global.error.BusinessException

/** Job 도메인의 JobNotFoundException과 별개다 — Application이 JobApplicationSnapshotQueryPort로
 * 조회했을 때 못 찾은 경우에 사용하며, Job 도메인 예외 Class를 직접 참조하지 않는다(Modulith 경계). */
class JobNotFoundException(
    jobId: Long,
) : BusinessException(ApplicationErrorCode.JOB_NOT_FOUND, "공고(id=$jobId)를 찾을 수 없습니다.")
