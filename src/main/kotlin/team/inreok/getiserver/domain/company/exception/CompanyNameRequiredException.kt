package team.inreok.getiserver.domain.company.exception

import team.inreok.getiserver.global.error.BusinessException

class CompanyNameRequiredException : BusinessException(CompanyErrorCode.COMPANY_NAME_REQUIRED)
