package team.inreok.getiserver.domain.job.exception

import team.inreok.getiserver.global.error.BusinessException

/** 공고가 참조하는 기업이 없거나 이미 삭제됐을 때 발생한다. */
class JobCompanyNotFoundException(
    companyId: Long,
) : BusinessException(JobErrorCode.COMPANY_NOT_FOUND, "기업(id=$companyId)을 찾을 수 없습니다.")
