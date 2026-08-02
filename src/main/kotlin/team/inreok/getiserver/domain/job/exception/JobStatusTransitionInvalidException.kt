package team.inreok.getiserver.domain.job.exception

import team.inreok.getiserver.domain.job.entity.type.JobStatus
import team.inreok.getiserver.global.error.BusinessException

class JobStatusTransitionInvalidException(
    current: JobStatus,
    target: JobStatus,
) : BusinessException(
        JobErrorCode.JOB_STATUS_TRANSITION_INVALID,
        "공고 상태를 $current 에서 $target (으)로 변경할 수 없습니다.",
    )
