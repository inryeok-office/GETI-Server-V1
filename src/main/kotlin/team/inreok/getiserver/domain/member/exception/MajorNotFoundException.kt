package team.inreok.getiserver.domain.member.exception

import team.inreok.getiserver.global.error.BusinessException

class MajorNotFoundException(
    majorIds: List<Long>,
) : BusinessException(MemberErrorCode.MAJOR_NOT_FOUND, "전공(id=$majorIds)을 찾을 수 없습니다.")
