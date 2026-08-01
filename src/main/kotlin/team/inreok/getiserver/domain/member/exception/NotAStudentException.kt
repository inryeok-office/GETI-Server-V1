package team.inreok.getiserver.domain.member.exception

import team.inreok.getiserver.global.error.BusinessException

class NotAStudentException : BusinessException(MemberErrorCode.NOT_A_STUDENT)
