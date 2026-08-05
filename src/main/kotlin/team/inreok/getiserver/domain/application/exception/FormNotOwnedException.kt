package team.inreok.getiserver.domain.application.exception

import team.inreok.getiserver.global.error.BusinessException

/** 양식은 존재하지만(FORM_NOT_FOUND와 구분) 수정·복제·활성화·보관을 요청한 사용자가 소유자가 아닐 때 사용한다. */
class FormNotOwnedException : BusinessException(ApplicationErrorCode.FORM_NOT_OWNED)
