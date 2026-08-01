package team.inreok.getiserver.domain.company.exception

import team.inreok.getiserver.global.error.BusinessException

class MouPeriodInvalidException : BusinessException(
    CompanyErrorCode.MOU_PERIOD_INVALID,
    "MOU 협약 시작일은 종료일보다 늦을 수 없습니다.",
)
