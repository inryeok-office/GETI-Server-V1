package team.inreok.getiserver.domain.job.exception

import team.inreok.getiserver.global.error.BusinessException

class JobNotFoundException(
    jobId: Long,
) : BusinessException(JobErrorCode.JOB_NOT_FOUND, "공고(id=$jobId)를 찾을 수 없습니다.")
