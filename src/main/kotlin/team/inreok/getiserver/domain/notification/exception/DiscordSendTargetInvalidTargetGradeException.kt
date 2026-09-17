package team.inreok.getiserver.domain.notification.exception

import team.inreok.getiserver.global.error.BusinessException

class DiscordSendTargetInvalidTargetGradeException :
    BusinessException(DiscordSendTargetErrorCode.INVALID_TARGET_GRADE)
