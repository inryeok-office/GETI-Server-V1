package team.inreok.getiserver.domain.notification.exception

import team.inreok.getiserver.global.error.BusinessException

class DiscordSendTargetPageTooDeepException : BusinessException(DiscordSendTargetErrorCode.PAGE_TOO_DEEP)
