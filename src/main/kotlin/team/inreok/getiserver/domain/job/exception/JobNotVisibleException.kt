package team.inreok.getiserver.domain.job.exception

import team.inreok.getiserver.global.error.BusinessException

class JobNotVisibleException(
    jobId: Long,
) : BusinessException(JobErrorCode.JOB_NOT_VISIBLE, "공고(id=$jobId)는 아직 공개되지 않았습니다.")
