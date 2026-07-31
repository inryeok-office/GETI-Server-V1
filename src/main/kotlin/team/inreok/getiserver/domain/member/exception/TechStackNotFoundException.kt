package team.inreok.getiserver.domain.member.exception

import team.inreok.getiserver.global.error.BusinessException

class TechStackNotFoundException(
    techStackIds: List<Long>,
) : BusinessException(MemberErrorCode.TECH_STACK_NOT_FOUND, "기술 스택(id=$techStackIds)을 찾을 수 없습니다.")
