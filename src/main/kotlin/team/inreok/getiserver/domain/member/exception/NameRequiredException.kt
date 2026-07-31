package team.inreok.getiserver.domain.member.exception

import team.inreok.getiserver.global.error.BusinessException

class NameRequiredException : BusinessException(MemberErrorCode.NAME_REQUIRED, "검색할 이름을 입력해야 합니다.")
