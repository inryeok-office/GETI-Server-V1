package team.inreok.getiserver.domain.application.service.impl

import team.inreok.getiserver.domain.application.dto.JobApplicationAdminAction
import team.inreok.getiserver.domain.application.entity.JobApplication
import team.inreok.getiserver.domain.application.entity.type.JobApplicationStatus
import team.inreok.getiserver.domain.application.exception.ApplicationActionNotAvailableException

// 교사 Action별 시작 가능 상태다(Issue #125 "상태 전이 / Flow" 절). 학생 Action의
// JOB_APPLICATION_ACTION_ALLOWED_FROM(JobApplicationServiceImpl.kt)과 대응 관계이며, 두 Phase가
// 서로 다른 Action Enum(JobApplicationAction/JobApplicationAdminAction)을 쓰므로 별도 Map으로 둔다.
private val JOB_APPLICATION_ADMIN_ACTION_ALLOWED_FROM: Map<JobApplicationAdminAction, JobApplicationStatus> =
    mapOf(
        JobApplicationAdminAction.ALLOW_EDIT to JobApplicationStatus.EDIT_REQUESTED,
        JobApplicationAdminAction.REQUEST_REVISION to JobApplicationStatus.SUBMITTED,
        JobApplicationAdminAction.APPROVE to JobApplicationStatus.SUBMITTED,
        JobApplicationAdminAction.REJECT to JobApplicationStatus.SUBMITTED,
    )

/**
 * JobApplicationAdminServiceImpl의 Method 개수를 detekt TooManyFunctions 한도 안에서 유지하기
 * 위해 순수 함수로 분리했다(JobApplicationServiceImpl.kt의 requireAllowedTransition과 같은 이유).
 */
fun requireAllowedAdminTransition(
    status: JobApplicationStatus,
    action: JobApplicationAdminAction,
) {
    val allowedFrom = JOB_APPLICATION_ADMIN_ACTION_ALLOWED_FROM.getValue(action)
    if (status != allowedFrom) {
        throw ApplicationActionNotAvailableException("현재 상태($status)에서는 $action Action을 수행할 수 없습니다.")
    }
}

fun applyJobApplicationAdminAction(
    application: JobApplication,
    action: JobApplicationAdminAction,
    reason: String?,
) {
    when (action) {
        JobApplicationAdminAction.ALLOW_EDIT -> {
            application.status = JobApplicationStatus.EDIT_ALLOWED
        }

        JobApplicationAdminAction.REQUEST_REVISION -> {
            application.status = JobApplicationStatus.REVISION_REQUESTED
            application.statusReason = reason
        }

        JobApplicationAdminAction.APPROVE -> {
            application.status = JobApplicationStatus.APPROVED
        }

        JobApplicationAdminAction.REJECT -> {
            application.status = JobApplicationStatus.REJECTED
            application.statusReason = reason
        }
    }
}
