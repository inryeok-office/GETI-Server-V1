package team.inreok.getiserver.domain.application.exception

import team.inreok.getiserver.global.error.BusinessException

class JobApplicationMethodNotInternalException :
    BusinessException(ApplicationErrorCode.JOB_APPLICATION_METHOD_NOT_INTERNAL)
