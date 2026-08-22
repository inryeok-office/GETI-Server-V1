package team.inreok.getiserver.domain.company.exception

import team.inreok.getiserver.global.error.BusinessException

class CompanyHasActiveJobsException : BusinessException(CompanyErrorCode.COMPANY_HAS_ACTIVE_JOBS)
