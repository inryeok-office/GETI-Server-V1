package team.inreok.getiserver.domain.company.exception

import team.inreok.getiserver.global.error.BusinessException

// 요청 값을 응답 Message에 되돌려주지 않고 Error Code의 기본 Message만 사용한다.
class DuplicateCompanyException : BusinessException(CompanyErrorCode.DUPLICATE_COMPANY)
