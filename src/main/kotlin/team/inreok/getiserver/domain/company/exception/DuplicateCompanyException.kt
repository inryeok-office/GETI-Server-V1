package team.inreok.getiserver.domain.company.exception

import team.inreok.getiserver.domain.company.entity.type.CompanyType
import team.inreok.getiserver.global.error.BusinessException

class DuplicateCompanyException(
    name: String,
    companyType: CompanyType,
) : BusinessException(
        CompanyErrorCode.DUPLICATE_COMPANY,
        "이미 등록된 기업입니다(name=$name, companyType=$companyType).",
    )
