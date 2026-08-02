package team.inreok.getiserver.domain.company.exception

import team.inreok.getiserver.global.error.BusinessException

class CompanyNotFoundException(
    companyId: Long,
) : BusinessException(CompanyErrorCode.COMPANY_NOT_FOUND, "기업(id=$companyId)을 찾을 수 없습니다.")
