package team.inreok.getiserver.domain.member.exception

import team.inreok.getiserver.global.error.BusinessException

class DuplicateMajorException : BusinessException(MemberErrorCode.DUPLICATE_MAJOR, "전공 목록에 중복된 값이 있습니다.")
